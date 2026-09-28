// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetryTest {

    private TestServer server;

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
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
    void retriesAnIdempotentGetOn503AndReturnsTheEventualSuccess() throws IOException {
        server = TestServer.start((ex, i) -> {
            if (i == 0) TestServer.json(ex, 503, "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"try later\"}");
            else TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":{\"ok\":true}}");
        });
        Map<String, Object> data = Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE);
        assertEquals(Map.of("ok", true), data);
        assertEquals(2, server.requests().size());
    }

    // The most important test in the SDK.
    @Test
    void neverRetriesAWriteEvenOn503() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 503, "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"try later\"}"));
        assertThrows(HighApiError.class, () -> Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("POST", "/orders", Transport.AuthKind.BEARER, Map.of(), Map.of()), MAP_TYPE));
        assertEquals(1, server.requests().size());
    }

    @Test
    void neverRetriesPatchOrDeleteEither() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 503, "{\"code\":\"SERVICE_UNAVAILABLE\"}"));
        assertThrows(HighApiError.class, () -> Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("PATCH", "/orders", Transport.AuthKind.BEARER, Map.of(), Map.of()), MAP_TYPE));
        assertThrows(HighApiError.class, () -> Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("DELETE", "/orders/1", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE));
        assertEquals(2, server.requests().size());
    }

    @Test
    void doesNotRetryA4xxThatIsNot429() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 400, "{\"code\":\"VALIDATION_ERROR\"}"));
        assertThrows(HighApiError.class, () -> Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE));
        assertEquals(1, server.requests().size());
    }

    @Test
    void givesUpAfterMaxRetriesAndThrowsTheLastError() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 500, "{\"code\":\"UNHANDLED_ERROR\"}"));
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.maxRetries(2)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE));
        assertEquals(500, error.status());
        assertEquals(3, server.requests().size()); // 1 initial + 2 retries
    }

    @Test
    void honoursANumericRetryAfterOn429() throws IOException {
        server = TestServer.start((ex, i) -> {
            if (i == 0) {
                try {
                    TestServer.jsonWithHeaders(ex, 429, "{\"code\":\"SERVICE_UNAVAILABLE\"}", Map.of("retry-after", "0"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}");
            }
        });
        Boolean data = Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE);
        assertEquals(Boolean.TRUE, data);
        assertEquals(2, server.requests().size());
    }

    @Test
    void warnsWhenItRetries() throws IOException {
        List<String> lines = new ArrayList<>();
        LogSink sink = new LogSink() {
            public void error(String m, Object d) { lines.add(m); }
            public void warn(String m, Object d) { lines.add(m); }
            public void info(String m, Object d) { lines.add(m); }
            public void debug(String m, Object d) { lines.add(m); }
        };
        server = TestServer.start((ex, i) -> {
            if (i == 0) TestServer.json(ex, 503, "{\"code\":\"SERVICE_UNAVAILABLE\"}");
            else TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}");
        });
        Transport.send(configFor(b -> b.logLevel(LogLevel.WARN).logSink(sink)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE);
        assertTrue(lines.stream().anyMatch(l -> l.toLowerCase(java.util.Locale.ROOT).contains("retry")));
    }

    // --- retryAfterMillis ----------------------------------------------

    @Test
    void readsADelayInSeconds() {
        assertEquals(2000L, Transport.retryAfterMillis("2", 0));
        assertEquals(0L, Transport.retryAfterMillis("0", 0));
    }

    @Test
    void readsAnHttpDateAndConvertsItToADelay() {
        long now = java.time.Instant.parse("2026-09-28T10:00:00Z").toEpochMilli();
        assertEquals(5000L, Transport.retryAfterMillis("Mon, 28 Sep 2026 10:00:05 GMT", now));
    }

    @Test
    void neverReturnsANegativeDelayForADateInThePast() {
        long now = java.time.Instant.parse("2026-09-28T10:00:10Z").toEpochMilli();
        assertEquals(0L, Transport.retryAfterMillis("Mon, 28 Sep 2026 10:00:05 GMT", now));
    }

    @Test
    void ignoresAMissingOrUnparseableHeaderSoBackoffTakesOver() {
        assertEquals(null, Transport.retryAfterMillis(null, 0));
        assertEquals(null, Transport.retryAfterMillis("soon", 0));
        assertEquals(null, Transport.retryAfterMillis("", 0));
    }

    // Finding class (Node review, replayed here): Retry-After had no ceiling.
    @Test
    void givesUpRatherThanSleepingPastMaxRetryDelayMs() throws IOException {
        server = TestServer.start((ex, i) -> {
            try {
                TestServer.jsonWithHeaders(ex, 429, "{\"code\":\"SERVICE_UNAVAILABLE\"}", Map.of("retry-after", "86400"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        long startedAt = System.currentTimeMillis();
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.maxRetries(2).maxRetryDelayMs(100)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), MAP_TYPE));
        assertEquals(429, error.status());
        assertTrue(System.currentTimeMillis() - startedAt < 2000);
        assertEquals(1, server.requests().size());
    }

    @Test
    void stillHonoursARetryAfterInsideTheCeiling() throws IOException {
        server = TestServer.start((ex, i) -> {
            if (i == 0) {
                try {
                    TestServer.jsonWithHeaders(ex, 429, "{\"code\":\"SERVICE_UNAVAILABLE\"}", Map.of("retry-after", "0"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}");
            }
        });
        Boolean data = Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null), BOOLEAN_TYPE);
        assertEquals(Boolean.TRUE, data);
        assertEquals(2, server.requests().size());
    }
}
