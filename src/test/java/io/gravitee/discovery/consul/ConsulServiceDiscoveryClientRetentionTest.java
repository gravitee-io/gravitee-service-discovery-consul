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

import io.gravitee.discovery.consul.configuration.ConsulServiceDiscoveryConfiguration;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards the shared-client workaround of {@link ConsulServiceDiscovery}: the Consul client of a stopped discovery must
 * leave the close future of the context that created it.
 */
class ConsulServiceDiscoveryClientRetentionTest {

    private static final int REDEPLOYS = 20;

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().await(10, TimeUnit.SECONDS);
    }

    @Test
    void should_not_retain_the_clients_of_stopped_discoveries_off_a_vertx_context() throws Exception {
        CloseFuture root = ((VertxInternal) vertx).closeFuture();
        int before = retained(root);

        for (int i = 0; i < REDEPLOYS; i++) {
            ConsulServiceDiscovery discovery = discovery();
            discovery.listen(event -> {});
            discovery.stop();
        }

        awaitRetained(root, before);
        assertThat(retained(root) - before).isZero();
    }

    @Test
    void should_not_retain_the_clients_of_stopped_discoveries_on_a_deployment_context() throws Exception {
        AtomicReference<CloseFuture> closeFuture = new AtomicReference<>();
        AtomicReference<Integer> before = new AtomicReference<>();

        vertx
            .deployVerticle(
                new AbstractVerticle() {
                    @Override
                    public void start() throws Exception {
                        closeFuture.set(((ContextInternal) context).closeFuture());
                        before.set(retained(closeFuture.get()));
                        for (int i = 0; i < REDEPLOYS; i++) {
                            ConsulServiceDiscovery discovery = discovery();
                            discovery.listen(event -> {});
                            discovery.stop();
                        }
                    }
                }
            )
            .await(10, TimeUnit.SECONDS);

        assertThat(closeFuture.get()).isNotSameAs(((VertxInternal) vertx).closeFuture());
        awaitRetained(closeFuture.get(), before.get());
        assertThat(retained(closeFuture.get()) - before.get()).isZero();
    }

    @Test
    void should_still_leak_when_the_client_comes_straight_from_vertx() throws Exception {
        CloseFuture root = ((VertxInternal) vertx).closeFuture();
        int before = retained(root);

        for (int i = 0; i < REDEPLOYS; i++) {
            vertx.createHttpClient(new HttpClientOptions()).close().await(10, TimeUnit.SECONDS);
        }

        // When this starts failing, Vert.x has been fixed (5.2): drop the shared-client workaround and this test class.
        assertThat(retained(root) - before).isEqualTo(REDEPLOYS);
    }

    private ConsulServiceDiscovery discovery() throws Exception {
        ConsulServiceDiscoveryConfiguration configuration = new ConsulServiceDiscoveryConfiguration();
        // Nothing listens there: the watch keeps failing, only the client created with it matters.
        configuration.setUrl("http://localhost:1");
        configuration.setService("my-service");

        ConsulServiceDiscovery discovery = new ConsulServiceDiscovery(configuration);
        Field field = ConsulServiceDiscovery.class.getDeclaredField("vertx");
        field.setAccessible(true);
        field.set(discovery, vertx);
        return discovery;
    }

    private static void awaitRetained(CloseFuture closeFuture, int expected) throws InterruptedException {
        // close() completes asynchronously, the nested close future unregisters itself once the client is closed.
        long deadline = System.currentTimeMillis() + 5_000;
        while (retained(closeFuture) != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    private static int retained(CloseFuture closeFuture) {
        try {
            Field children = CloseFuture.class.getDeclaredField("children");
            children.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) children.get(closeFuture);
            return map == null ? 0 : map.size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
