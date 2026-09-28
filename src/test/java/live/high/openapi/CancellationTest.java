// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CancellationTest {

    private TestServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "test-scheduler");
        t.setDaemon(true);
        return t;
    });

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
        scheduler.shutdownNow();
    }

    private ResolvedConfig configFor(Consumer<HighClientOptions.Builder> customize) {
        HighClientOptions.Builder builder = HighClientOptions.builder()
            .baseUrl(server.baseUrl()).accessToken("tok").timeoutMs(2000);
        customize.accept(builder);
        return ResolvedConfig.resolve(builder.build(), Map.of());
    }

    private static final TypeReference<Boolean> BOOLEAN_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    void surfacesACallerCancelWithoutRetryingIt() throws IOException {
        server = TestServer.start((ex, i) -> {
            try {
                Thread.sleep(1000);
                TestServer.json(ex, 503, "{\"code\":\"SERVICE_UNAVAILABLE\"}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        CompletableFuture<Boolean> future = Transport.sendAsync(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE);
        scheduler.schedule(() -> future.cancel(true), 20, TimeUnit.MILLISECONDS);

        // CompletableFuture.join() throws a bare CancellationException (never
        // wrapped in CompletionException) when the future itself was cancelled —
        // this is what "surfaced unchanged, never converted into a timeout"
        // looks like in Java's own idiom.
        assertThrows(CancellationException.class, future::join);
        assertEquals(1, server.requests().size());
    }

    @Test
    void doesNotSendAnotherRequestAfterTheCallerCancelsMidRetryDelay() throws IOException {
        server = TestServer.start((ex, i) -> {
            try {
                TestServer.jsonWithHeaders(ex, 429, "{\"code\":\"SERVICE_UNAVAILABLE\"}", Map.of("retry-after", "3"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        CompletableFuture<Boolean> future = Transport.sendAsync(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE);
        scheduler.schedule(() -> future.cancel(true), 100, TimeUnit.MILLISECONDS);

        assertThrows(CancellationException.class, future::join);
        // Give the (now cancelled) delay time to have fired a second request if the
        // cancellation had been ignored, before asserting it did not.
        sleepQuietly(400);
        assertEquals(1, server.requests().size());
    }

    @Test
    void reportsTheSdkTimeoutAsATimeoutNotAsAnAbort() throws IOException {
        server = TestServer.start((ex, i) -> { /* never responds */ });
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.timeoutMs(50).maxRetries(0)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE));
        assertEquals(0, error.status());
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timed out"));
    }

    @Test
    void timesOutWithHighApiErrorRatherThanHanging() throws IOException {
        server = TestServer.start((ex, i) -> { /* never responds */ });
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.timeoutMs(50).maxRetries(0)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE));
        assertEquals(0, error.status());
    }

    @Test
    void timesOutWhenTheBodyStallsAfterTheHeadersArrive() throws IOException {
        server = TestServer.start((ex, i) ->
            TestServer.stalledBody(ex, "{\"requestId\":\"r\",\"da", 3000, "ta\":true}"));
        long startedAt = System.currentTimeMillis();
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.timeoutMs(150).maxRetries(0)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timed out"));
        assertTrue(System.currentTimeMillis() - startedAt < 2000);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
