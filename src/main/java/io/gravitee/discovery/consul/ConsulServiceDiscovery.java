/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.discovery.consul;

import io.gravitee.discovery.api.ServiceDiscovery;
import io.gravitee.discovery.api.event.Event;
import io.gravitee.discovery.api.event.Handler;
import io.gravitee.discovery.api.service.AbstractServiceDiscovery;
import io.gravitee.discovery.consul.configuration.ConsulServiceDiscoveryConfiguration;
import io.gravitee.discovery.consul.configuration.KeyStoreType;
import io.gravitee.discovery.consul.configuration.TrustStoreType;
import io.gravitee.discovery.consul.service.ConsulService;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.consul.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConsulServiceDiscovery extends AbstractServiceDiscovery<ConsulService> implements ServiceDiscovery {

    private final static Logger LOGGER = LoggerFactory.getLogger(ConsulServiceDiscovery.class);

    private static final String HTTPS_SCHEME = "https";
    private static final int CONSUL_DEFAULT_PORT = 8500;

    @Autowired
    private Vertx vertx;

    private final ConsulServiceDiscoveryConfiguration configuration;

    private Watch<ServiceEntryList> watcher;

    public ConsulServiceDiscovery(final ConsulServiceDiscoveryConfiguration configuration) {
        this.configuration = configuration;
    }

    public void stop() throws Exception {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Override
    public void listen(Handler<Event> handler) {
        URI consulUri = URI.create(configuration.getUrl());

        ConsulClientOptions options = new ConsulClientOptions()
                .setHost(consulUri.getHost())
                .setPort((consulUri.getPort() != -1) ? consulUri.getPort() : CONSUL_DEFAULT_PORT)
                .setDc(configuration.getDc())
                .setAclToken(configuration.getAcl());

        if (HTTPS_SCHEME.equalsIgnoreCase(consulUri.getScheme())) {
            // SSL is not configured but the endpoint scheme is HTTPS so let's enable the SSL on Vert.x HTTP client
            // automatically
            options.setSsl(true);

            // Configure keystore
            if (configuration.getKeyStoreType() == null || configuration.getKeyStoreType() == KeyStoreType.NONE) {
                options.setTrustAll(true);
            } else {
                if (configuration.getKeyStoreType() == KeyStoreType.JKS) {
                    JksOptions jksOptions = new JksOptions();
                    jksOptions.setPassword(configuration.getKeyStorePassword());

                    if (configuration.getKeyStoreContent() != null && configuration.getKeyStoreContent().isEmpty()) {
                        jksOptions.setValue(Buffer.buffer(configuration.getKeyStoreContent()));
                    }

                    jksOptions.setPath(configuration.getKeyStorePath());

                    options.setKeyStoreOptions(jksOptions);
                } else if (configuration.getKeyStoreType() == KeyStoreType.PKCS12) {
                    PfxOptions pfxOptions = new PfxOptions();
                    pfxOptions.setPassword(configuration.getKeyStorePassword());

                    if (configuration.getKeyStoreContent() != null && configuration.getKeyStoreContent().isEmpty()) {
                        pfxOptions.setValue(Buffer.buffer(configuration.getKeyStoreContent()));
                    }

                    pfxOptions.setPath(configuration.getKeyStorePath());

                    options.setPfxKeyCertOptions(pfxOptions);
                }
            }

            // Configure truststore
            if (configuration.getTrustStoreType() == null || configuration.getTrustStoreType() == TrustStoreType.NONE) {
                options.setTrustAll(true);
            } else {
                if (configuration.getTrustStoreType() == TrustStoreType.JKS) {
                    JksOptions jksOptions = new JksOptions();
                    jksOptions.setPassword(configuration.getTrustStorePassword());
                    jksOptions.setPath(configuration.getTrustStorePath());

                    if (configuration.getTrustStoreContent() != null && configuration.getTrustStoreContent().isEmpty()) {
                        jksOptions.setValue(Buffer.buffer(configuration.getTrustStoreContent()));
                    }

                    options.setTrustStoreOptions(jksOptions);
                } else if (configuration.getTrustStoreType() == TrustStoreType.PKCS12) {
                    PfxOptions pfxOptions = new PfxOptions();
                    pfxOptions.setPassword(configuration.getTrustStorePassword());
                    pfxOptions.setPath(configuration.getTrustStorePath());

                    if (configuration.getTrustStoreContent() != null && !configuration.getTrustStoreContent().isEmpty()) {
                        pfxOptions.setValue(io.vertx.core.buffer.Buffer.buffer(configuration.getTrustStoreContent()));
                    }

                    options.setPfxTrustOptions(pfxOptions);
                }
            }
        }

        LOGGER.info("Consul.io configuration: endpoint[{}] dc[{}] acl[{}]", consulUri.toString(),
                options.getDc(), options.getAclToken());

        watcher = Watch.service(configuration.getService(), vertx, options);
        watcher.setHandler(event -> {
            if (event.succeeded()) {
                LOGGER.debug("Receiving a Consul.io watch event for service: name[{}]", configuration.getService());

                List<ServiceEntry> entries = event.nextResult().getList();
                // Handle new services or updated services
                for (ServiceEntry service : event.nextResult().getList()) {
                    Service service1 = service.getService();
                    service1.setNodeAddress(service.getNode().getAddress());

                    ConsulService consulService = new ConsulService(service1);

                    // Get previous service reference
                    ConsulService oldService = getService(consulService::equals);

                    // Endpoint does not exist (according to its name)
                    if (oldService == null) {
                        LOGGER.info("Register a new service from Consul.io: id[{}] name[{}]",
                                service1.getId(), service1.getName());
                        handler.handle(registerEndpoint(consulService));
                    } else {
                        // Update it only if target has been changed
                        if (consulService.port() != oldService.port() ||
                                ! consulService.host().equals(oldService.host())) {
                            LOGGER.info("Update an existing service from Consul.io: id[{}] name[{}] address[{}:{}]",
                                    service1.getId(), service1.getName(), consulService.host(), consulService.port());
                            handler.handle(unregisterEndpoint(oldService));
                            handler.handle(registerEndpoint(consulService));
                        }
                    }
                }

                // Handle de-registered services
                if (event.prevResult() != null) {
                    List<ServiceEntry> oldEntries = event.prevResult().getList();
                    if (oldEntries.size() > entries.size()) {
                        // Select only de-registered services
                        oldEntries.removeAll(entries);
                        for (ServiceEntry oldEntry : oldEntries) {
                            LOGGER.info("Remove a de-registered service from Consul.io: id[{}] name[{}]",
                                    oldEntry.getService().getId(), oldEntry.getService().getName());
                            final ConsulService consulService = new ConsulService(oldEntry.getService());
                            handler.handle(unregisterEndpoint(consulService));
                        }
                    }
                }
            } else {
                LOGGER.error("Unexpected error while watching services catalog", event.cause());
            }
        }).start();
    }
}
