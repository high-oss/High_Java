// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransportBasicsTest {

    private TestServer server;

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    private ResolvedConfig configFor(Consumer<HighClientOptions.Builder> customize) {
        HighClientOptions.Builder builder = HighClientOptions.builder()
            .baseUrl(server.baseUrl()).accessToken("tok").apiKey("key");
        customize.accept(builder);
        return ResolvedConfig.resolve(builder.build(), Map.of());
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Boolean> BOOLEAN_TYPE = new TypeReference<>() {};
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {};

    @Test
    void unwrapsTheEnvelopeAndReturnsOnlyData() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"a1b2c3\",\"data\":{\"orderId\":\"1\"}}"));
        Map<String, Object> data = Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            MAP_TYPE);
        assertEquals(Map.of("orderId", "1"), data);
    }

    @Test
    void sendsTheBearerTokenForBearerOperationsAndNoApiKey() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            BOOLEAN_TYPE);
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("Bearer tok", req.headers().get("authorization"));
        assertNull(req.headers().get("x-api-key"));
    }

    @Test
    void sendsTheApiKeyForApiKeyOperationsAndNoBearerToken() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/auth/generate-access-token", Transport.AuthKind.API_KEY, Map.of(), null),
            BOOLEAN_TYPE);
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("key", req.headers().get("x-api-key"));
        assertNull(req.headers().get("authorization"));
    }

    @Test
    void failsBeforeSendingWhenTheRequiredCredentialIsMissing() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        ResolvedConfig config = ResolvedConfig.resolve(HighClientOptions.builder().baseUrl(server.baseUrl()).build(), Map.of());
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(config,
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            BOOLEAN_TYPE));
        assertTrue(error.getMessage().contains("accessToken"));
        assertEquals(0, server.requests().size());
    }

    @Test
    void serialisesAJsonBodyAndSetsContentType() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":{}}"));
        Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("POST", "/orders", Transport.AuthKind.BEARER, Map.of(), Map.of("quantity", 10)),
            MAP_TYPE);
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("{\"quantity\":10}", req.body());
        assertTrue(req.headers().get("content-type").contains("application/json"));
    }

    @Test
    void appendsDefinedQueryParametersAndSkipsUndefinedOnes() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("clientId", "C1");
        query.put("tOtp", "123456");
        Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/auth/generate-access-token", Transport.AuthKind.API_KEY, query, null),
            BOOLEAN_TYPE);
        String path = server.requests().get(0).path();
        assertTrue(path.contains("clientId=C1"));
        assertTrue(path.contains("tOtp=123456"));
    }

    @Test
    void mapsAnErrorEnvelopeToHighApiError() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 400,
            "{\"requestId\":\"a1b2c3\",\"code\":\"ORDER_REJECTED\",\"message\":\"Insufficient funds\"}"));
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("POST", "/orders", Transport.AuthKind.BEARER, Map.of(), Map.of()),
            MAP_TYPE));
        assertEquals("ORDER_REJECTED", error.code());
        assertEquals("a1b2c3", error.requestId());
    }

    @Test
    void turnsANonJsonErrorBodyIntoHighApiErrorNotAParseFailure() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.text(ex, 502, "text/html", "<html><body>502 Bad Gateway</body></html>"));
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(
            configFor(b -> b.maxRetries(0)),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            MAP_TYPE));
        assertEquals(502, error.status());
    }

    @Test
    void turnsA204WithNoBodyIntoNullRatherThanAParseError() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.noContent(ex));
        Map<String, Object> data = Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("DELETE", "/orders/1", Transport.AuthKind.BEARER, Map.of(), null),
            MAP_TYPE);
        assertNull(data);
    }

    @Test
    void sendsAUserAgentIdentifyingTheSdk() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Transport.send(configFor(b -> {}),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            BOOLEAN_TYPE);
        assertTrue(server.requests().get(0).headers().get("user-agent").contains("high-sdk-java"));
    }

    // --- logging -------------------------------------------------------

    private static List<String> capture(Consumer<HighClientOptions.Builder> unused) {
        return new ArrayList<>();
    }

    private LogSink capturingSink(List<String> lines) {
        return new LogSink() {
            public void error(String m, Object d) { lines.add(d == null ? m : m + " " + d); }
            public void warn(String m, Object d) { lines.add(d == null ? m : m + " " + d); }
            public void info(String m, Object d) { lines.add(d == null ? m : m + " " + d); }
            public void debug(String m, Object d) { lines.add(d == null ? m : m + " " + d); }
        };
    }

    @Test
    void printsNothingByDefault() throws IOException {
        List<String> lines = new ArrayList<>();
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Transport.send(configFor(b -> b.logSink(capturingSink(lines))),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            BOOLEAN_TYPE);
        assertTrue(lines.isEmpty());
    }

    @Test
    void logsTheRequestAndTheResponseStatusAtDebug() throws IOException {
        List<String> lines = new ArrayList<>();
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"a1b2c3\",\"data\":true}"));
        Transport.send(configFor(b -> b.logLevel(LogLevel.DEBUG).logSink(capturingSink(lines))),
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            BOOLEAN_TYPE);
        String all = String.join("\n", lines);
        assertTrue(all.contains("GET"));
        assertTrue(all.contains("orders/list"));
        assertTrue(all.contains("200"));
    }

    @Test
    void neverLogsTheBearerTokenTheApiKeyOrTheTotp() throws IOException {
        List<String> lines = new ArrayList<>();
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":true}"));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("clientId", "C1");
        query.put("tOtp", "123456");
        Transport.send(configFor(b -> b.logLevel(LogLevel.DEBUG).logSink(capturingSink(lines))
                .accessToken("SECRET-TOKEN").apiKey("SECRET-KEY")),
            new Transport.RequestSpec("GET", "/auth/generate-access-token", Transport.AuthKind.API_KEY, query, null),
            BOOLEAN_TYPE);
        String all = String.join("\n", lines);
        assertFalse(all.contains("SECRET-TOKEN"));
        assertFalse(all.contains("SECRET-KEY"));
        assertFalse(all.contains("123456"));
        assertTrue(all.contains("REDACTED"));
    }
}
