// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.AccessToken;

/**
 * TOTP authentication.
 *
 * <p>The redirect consent flow ({@code /auth/generate-consent}, {@code
 * /auth/login}, {@code /auth/consume-consent}) is deliberately not wrapped:
 * it is built around a browser page only a human can complete. Token
 * introspection ({@code /auth/validate-token}) is not wrapped either — a
 * caller learns a token is invalid from the next call's 401. See the README.
 */
public final class AuthResource {

    private static final TypeReference<AccessToken> ACCESS_TOKEN = new TypeReference<>() {};

    private final ResolvedConfig config;

    public AuthResource(ResolvedConfig config) {
        this.config = config;
    }

    /** Exchanges the API key plus a TOTP for a 24-hour access token. */
    public AccessToken generateAccessToken(String clientId, String tOtp) {
        return Transport.send(config, requestSpec(clientId, tOtp), ACCESS_TOKEN);
    }

    public CompletableFuture<AccessToken> generateAccessTokenAsync(String clientId, String tOtp) {
        return Transport.sendAsync(config, requestSpec(clientId, tOtp), ACCESS_TOKEN);
    }

    private static Transport.RequestSpec requestSpec(String clientId, String tOtp) {
        Map<String, String> query = new java.util.LinkedHashMap<>();
        query.put("clientId", clientId);
        query.put("tOtp", tOtp);
        return new Transport.RequestSpec(
            "GET", "/auth/generate-access-token", Transport.AuthKind.API_KEY, query, null);
    }
}
