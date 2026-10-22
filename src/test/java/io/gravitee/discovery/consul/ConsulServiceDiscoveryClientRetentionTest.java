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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guards the shared-client workaround of {@link ConsulServiceDiscovery}: the Consul client of a stopped discovery must
 * leave the close future of the context that created it, once its connection to Consul is closed.
 */
@Testcontainers
class ConsulServiceDiscoveryClientRetentionTest {

    private static final int REDEPLOYS = 10;
    private static final int CONSUL_PORT = 8500;
    private static final String SERVICE_NAME = "my-service";

    @Container
    static final GenericContainer<?> CONSUL = new GenericContainer<>("hashicorp/consul:1.15")
        .withCommand("agent", "-dev", "-client", "0.0.0.0")
        .withExposedPorts(CONSUL_PORT);

    private Vertx vertx;
    private ConsulClient consul;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        consul = ConsulClient.create(vertx, new ConsulClientOptions().setHost(CONSUL.getHost()).setPort(CONSUL.getMappedPort(CONSUL_PORT)));
        consul
            .registerService(new ServiceOptions().setName(SERVICE_NAME).setId("id1").setAddress("10.0.0.1").setPort(8048))
            .await(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        consul.deregisterService("id1").await(10, TimeUnit.SECONDS);
        consul.close();
        vertx.close().await(10, TimeUnit.SECONDS);
    }

    @Test
    void should_not_retain_the_clients_of_stopped_discoveries_off_a_vertx_context() throws Exception {
        CloseFuture root = ((VertxInternal) vertx).closeFuture();
        int before = retained(root);
        AtomicInteger announced = new AtomicInteger();

        for (int i = 0; i < REDEPLOYS; i++) {
            BlockingQueue<Event> events = new LinkedBlockingQueue<>();
            ConsulServiceDiscovery discovery = discovery();
            discovery.listen(events::add);
            awaitRegistered(events, announced);
            discovery.stop();
        }

        // Without this the test would pass vacuously: every watch got an answer from Consul over a real connection.
        assertThat(announced).hasValue(REDEPLOYS);
        assertThat(awaitRetained(root, before) - before).isZero();
    }

    @Test
    void should_not_retain_the_clients_of_stopped_discoveries_on_a_deployment_context() throws Exception {
        Context deployment = deployVerticle();
        CloseFuture closeFuture = ((ContextInternal) deployment).closeFuture();
        assertThat(closeFuture).isNotSameAs(((VertxInternal) vertx).closeFuture());
        int before = retained(closeFuture);
        AtomicInteger announced = new AtomicInteger();

        for (int i = 0; i < REDEPLOYS; i++) {
            BlockingQueue<Event> events = new LinkedBlockingQueue<>();
            ConsulServiceDiscovery discovery = discovery();
            // The watch is created on the deployment context; it is stopped from the test thread, as the gateway does.
            Promise<Void> listening = Promise.promise();
            deployment.runOnContext(v -> {
                discovery.listen(events::add);
                listening.complete();
            });
            listening.future().await(10, TimeUnit.SECONDS);
            awaitRegistered(events, announced);
            discovery.stop();
        }

        assertThat(announced).hasValue(REDEPLOYS);
        assertThat(awaitRetained(closeFuture, before) - before).isZero();
    }

    @Test
    void should_still_leak_when_the_client_comes_straight_from_vertx() throws Exception {
        // This watches the Vert.x version this module is built against (from gravitee-bom), not the one APIM ships:
        // when it starts failing, remove the workaround here, the gateway being on a fixed Vert.x too.
        Context deployment = deployVerticle();
        CloseFuture closeFuture = ((ContextInternal) deployment).closeFuture();
        int before = retained(closeFuture);

        for (int i = 0; i < REDEPLOYS; i++) {
            Promise<Void> closed = Promise.promise();
            deployment.runOnContext(v -> vertx.createHttpClient(new HttpClientOptions()).close().onComplete(closed));
            closed.future().await(10, TimeUnit.SECONDS);
        }

        assertThat(retained(closeFuture) - before)
            .as("Vert.x no longer leaks closed clients: remove the shared-client workaround of ConsulServiceDiscovery and this test")
            .isEqualTo(REDEPLOYS);
    }

    private ConsulServiceDiscovery discovery() throws Exception {
        ConsulServiceDiscoveryConfiguration configuration = new ConsulServiceDiscoveryConfiguration();
        configuration.setUrl("http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(CONSUL_PORT));
        configuration.setService(SERVICE_NAME);

        ConsulServiceDiscovery discovery = new ConsulServiceDiscovery(configuration);
        Field field = ConsulServiceDiscovery.class.getDeclaredField("vertx");
        field.setAccessible(true);
        field.set(discovery, vertx);
        return discovery;
    }

    private Context deployVerticle() throws Exception {
        Promise<Context> started = Promise.promise();
        vertx
            .deployVerticle(
                new AbstractVerticle() {
                    @Override
                    public void start() {
                        started.complete(context);
                    }
                }
            )
            .await(10, TimeUnit.SECONDS);
        return started.future().await(10, TimeUnit.SECONDS);
    }

    private static void awaitRegistered(BlockingQueue<Event> events, AtomicInteger announced) throws InterruptedException {
        Event event = events.poll(30, TimeUnit.SECONDS);
        assertThat(event).as("the watch got no answer from Consul").isNotNull();
        assertThat(event.type()).isEqualTo(EventType.REGISTER);
        announced.incrementAndGet();
    }

    /** Waits for close() to complete asynchronously and returns the last count observed. */
    private static int awaitRetained(CloseFuture closeFuture, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        int retained = retained(closeFuture);
        while (retained != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            retained = retained(closeFuture);
        }
        return retained;
    }

    private static int retained(CloseFuture closeFuture) {
        try {
            Field children = CloseFuture.class.getDeclaredField("children");
            children.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) children.get(closeFuture);
            return map == null ? 0 : map.size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "CloseFuture.children is gone: Vert.x internals changed, this does not mean the leak is fixed. " +
                    "Re-check how closed clients are tracked before touching the shared-client workaround.",
                e
            );
        }
    }
}
