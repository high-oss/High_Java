// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.AccessToken;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthResourceTest {

    private TestServer server;

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    @Test
    void generateAccessTokenSendsTheApiKeyAndQueryParamsAndParsesTheToken() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200,
            "{\"requestId\":\"a1b2c3\",\"data\":{\"accessToken\":\"eyJhbGciOi\",\"expiresAt\":\"2026-09-28T06:30:56.123Z\"}}"));
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).apiKey("key").build(), java.util.Map.of());
        AuthResource auth = new AuthResource(config);

        AccessToken token = auth.generateAccessToken("C1", "123456");

        assertEquals("eyJhbGciOi", token.getAccessToken());
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("GET", req.method());
        assertTrue(req.path().startsWith("/v1/auth/generate-access-token"));
        assertTrue(req.path().contains("clientId=C1"));
        assertTrue(req.path().contains("tOtp=123456"));
        assertEquals("key", req.headers().get("x-api-key"));
        assertNull(req.headers().get("authorization"));
    }
}
