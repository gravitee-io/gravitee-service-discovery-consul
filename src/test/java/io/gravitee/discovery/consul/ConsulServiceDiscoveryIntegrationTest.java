/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.discovery.consul;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.discovery.api.event.Event;
import io.gravitee.discovery.api.event.EventType;
import io.gravitee.discovery.consul.configuration.ConsulServiceDiscoveryConfiguration;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the discovery against a real Consul agent, on the Vert.x version the gateway provides.
 */
@Testcontainers
class ConsulServiceDiscoveryIntegrationTest {

    private static final String SERVICE_NAME = "my-service";
    private static final int CONSUL_PORT = 8500;

    @Container
    static final GenericContainer<?> CONSUL = new GenericContainer<>("hashicorp/consul:1.15")
        .withCommand("agent", "-dev", "-client", "0.0.0.0")
        .withExposedPorts(CONSUL_PORT);

    private Vertx vertx;
    private ConsulClient consul;
    private ConsulServiceDiscovery discovery;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        consul = ConsulClient.create(vertx, new ConsulClientOptions().setHost(CONSUL.getHost()).setPort(CONSUL.getMappedPort(CONSUL_PORT)));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (discovery != null) {
            discovery.stop();
        }
        consul.close();
        vertx.close().await(10, TimeUnit.SECONDS);
    }

    @Test
    void should_register_then_unregister_a_service_known_by_consul() throws Exception {
        consul
            .registerService(new ServiceOptions().setName(SERVICE_NAME).setId("id1").setAddress("10.0.0.1").setPort(8048))
            .await(10, TimeUnit.SECONDS);
        BlockingQueue<Event> events = new LinkedBlockingQueue<>();

        discovery = discovery();
        discovery.listen(events::add);

        Event registered = events.poll(30, TimeUnit.SECONDS);
        assertThat(registered).isNotNull();
        assertThat(registered.type()).isEqualTo(EventType.REGISTER);
        assertThat(registered.service().host()).isEqualTo("10.0.0.1");
        assertThat(registered.service().port()).isEqualTo(8048);

        consul.deregisterService("id1").await(10, TimeUnit.SECONDS);

        Event unregistered = events.poll(30, TimeUnit.SECONDS);
        assertThat(unregistered).isNotNull();
        assertThat(unregistered.type()).isEqualTo(EventType.UNREGISTER);
        assertThat(unregistered.service().host()).isEqualTo("10.0.0.1");
    }

    private ConsulServiceDiscovery discovery() throws Exception {
        ConsulServiceDiscoveryConfiguration configuration = new ConsulServiceDiscoveryConfiguration();
        configuration.setUrl("http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(CONSUL_PORT));
        configuration.setService(SERVICE_NAME);

        ConsulServiceDiscovery consulServiceDiscovery = new ConsulServiceDiscovery(configuration);
        Field field = ConsulServiceDiscovery.class.getDeclaredField("vertx");
        field.setAccessible(true);
        field.set(consulServiceDiscovery, vertx);
        return consulServiceDiscovery;
    }
}
