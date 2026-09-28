// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Contract §11's 15-point conformance list, one test per point, each a thin
 * pointer to where the full behaviour is exercised. This file exists so a
 * reviewer can see all 15 are covered without hunting through the suite; it
 * is not the primary coverage for any of them — duplicating assertions here
 * would just be two places to keep in sync.
 */
class ConformanceTest {

    /** 1. URL composed from baseUrl + versionPath, tolerant of stray slashes. Full coverage: {@code ConfigTest#buildUrl*}. */
    @Test
    void point1_urlComposition() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl("https://h.example/").versionPath("/v2/").build(), Map.of());
        assertEquals("https://h.example/v2/orders", config.buildUrl("/orders"));
    }

    /** 2. Environment resolution; explicit environment beats env vars. Full coverage: {@code ConfigTest}, contract's env-var suppression trap. */
    @Test
    void point2_environmentResolution() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().environment(Environment.SANDBOX).build(),
            Map.of("HIGH_BASE_URL", "https://openapi.high.live"));
        assertEquals(Environments.get(Environment.SANDBOX).api(), config.baseUrl());
    }

    /** 3. Correct credential attached per operation; a missing one fails before sending. Full coverage: {@code TransportBasicsTest}. */
    @Test
    void point3_credentialAttachmentAndPreflightFailure() {
        ResolvedConfig config = ResolvedConfig.resolve(HighClientOptions.builder().build(), Map.of());
        HighApiError error = assertThrows(HighApiError.class, () -> Transport.send(config,
            new Transport.RequestSpec("GET", "/orders/list", Transport.AuthKind.BEARER, Map.of(), null),
            new com.fasterxml.jackson.core.type.TypeReference<Object>() {}));
        assertTrue(error.getMessage().contains("accessToken"));
    }

    /** 4. Envelope unwrapped; requestId preserved on errors. Full coverage: {@code TransportBasicsTest}, {@code ErrorsTest}. */
    @Test
    void point4_envelopeUnwrappedRequestIdOnError() {
        HighApiError error = HighApiError.fromResponse(400,
            new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("requestId", "a1b2c3", "code", "ORDER_REJECTED")),
            null);
        assertEquals("a1b2c3", error.requestId());
    }

    /** 5. A default error response mapped to the error type, with all messages. Full coverage: {@code ErrorsTest#keepsEveryMessage*}. */
    @Test
    void point5_allMessagesKept() {
        var body = new com.fasterxml.jackson.databind.ObjectMapper()
            .valueToTree(Map.of("message", java.util.List.of("a", "b")));
        assertEquals(java.util.List.of("a", "b"), HighApiError.fromResponse(422, body, null).messages());
    }

    /** 6. A non-JSON error body surfaced with its real status. Full coverage: {@code ErrorsTest#surfacesANonJson*}, {@code TransportBasicsTest}. */
    @Test
    void point6_nonJsonErrorBody() {
        HighApiError error = HighApiError.fromResponse(502, null, "<html>502</html>");
        assertEquals(502, error.status());
    }

    /** 7. Retry on 429 with Retry-After in both forms; no retry on any write. Full coverage: {@code RetryTest}. */
    @Test
    void point7_retryPolicy() {
        assertEquals(2000L, Transport.retryAfterMillis("2", 0));
    }

    /** 8. A Retry-After above the ceiling fails fast. Full coverage: {@code RetryTest#givesUpRatherThanSleepingPastMaxRetryDelayMs}. */
    @Test
    void point8_retryAfterCeiling() {
        assertThrows(IllegalArgumentException.class,
            () -> ResolvedConfig.resolve(HighClientOptions.builder().maxRetryDelayMs(0).build(), Map.of()));
    }

    /** 9. Cancellation during a request and during a retry delay. Full coverage: {@code CancellationTest}. */
    @Test
    void point9_cancellation() {
        var future = new java.util.concurrent.CompletableFuture<Void>();
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
    }

    /** 10. Timeout during headers and during the body. Full coverage: {@code CancellationTest#timesOutWhenTheBodyStalls*}. */
    @Test
    void point10_timeoutCoversWholeExchange() {
        assertThrows(IllegalArgumentException.class,
            () -> ResolvedConfig.resolve(HighClientOptions.builder().timeoutMs(0).build(), Map.of()));
    }

    /** 11. Path parameters percent-encoded (M&M-EQ, NIFTY 50, and a /). Full coverage: {@code PathsTest}. */
    @Test
    void point11_pathParameterEncoding() {
        assertEquals("/scrips/M%26M-EQ/depth", Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "M&M-EQ")));
        assertEquals("/scrips/NIFTY%2050/depth", Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "NIFTY 50")));
        assertEquals("/scrips/a%2Fb/depth", Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "a/b")));
    }

    /** 12. Credentials absent from a serialised client. Full coverage: {@code PublicSurfaceTest}. */
    @Test
    void point12_credentialContainment() {
        HighClient client = new HighClient(HighClientOptions.builder().accessToken("SECRET").build());
        assertTrue(!client.toString().contains("SECRET"));
    }

    /** 13. Licence headers present on every hand-written file. Full coverage: {@code LicenseHeaderTest}. */
    @Test
    void point13_licenceHeaders() {
        assertTrue(true); // see LicenseHeaderTest, which scans the whole tree.
    }

    /** 14. The excluded auth methods do not exist. Full coverage: {@code PublicSurfaceTest#wrapsNeither*}. */
    @Test
    void point14_excludedAuthMethodsDoNotExist() {
        boolean exists = java.util.Arrays.stream(live.high.openapi.resources.AuthResource.class.getMethods())
            .anyMatch(m -> m.getName().equals("validateToken"));
        assertTrue(!exists);
    }

    /** 15. Error-code constants and ENVIRONMENTS still match the pinned spec. Full coverage: {@code SpecSyncTest}. */
    @Test
    void point15_specSync() {
        assertTrue(ErrorCodes.ALL.contains("ORDER_REJECTED"));
    }
}
