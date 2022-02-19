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
package io.gravitee.discovery.consul.service;

import io.gravitee.discovery.api.service.Service;
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConsulService implements Service {

    private static final String CONSUL_ID_PREFIX = "consul:";

    private static final String CONSUL_SSL_METADATA = "gravitee_ssl";
    private static final String CONSUL_PATH_METADATA = "gravitee_path";

    private final io.vertx.ext.consul.Service service;
    private final String address;
    private final String id;
    private String scheme = HTTP_SCHEME;
    private String path = DEFAULT_BASE_PATH;

    public ConsulService(final io.vertx.ext.consul.Service service) {
        this.service = service;

        if (service.getAddress() == null || service.getAddress().trim().isEmpty()) {
            this.address = service.getNodeAddress();
        } else {
            this.address = service.getAddress();
        }

        this.id = CONSUL_ID_PREFIX + service.getId();

        String sslMetadata = (service.getMeta() != null) ? service.getMeta().get(CONSUL_SSL_METADATA) : null;
        if (Boolean.parseBoolean(sslMetadata)) {
            scheme = HTTPS_SCHEME;
        }

        String pathMetadata = (service.getMeta() != null) ? service.getMeta().get(CONSUL_PATH_METADATA) : null;
        if (pathMetadata != null) {
            path = pathMetadata;
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String host() {
        return address;
    }

    @Override
    public int port() {
        return service.getPort();
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public String basePath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsulService that = (ConsulService) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
