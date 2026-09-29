// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static ResolvedConfig resolve(HighClientOptions options, Map<String, String> env) {
        return ResolvedConfig.resolve(options, env);
    }

    private static ResolvedConfig resolve(HighClientOptions options) {
        return resolve(options, Map.of());
    }

    @Test
    void defaultsToProductionV1() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build());
        assertEquals("https://openapi.high.live", config.baseUrl());
        assertEquals("v1", config.versionPath());
        assertEquals(30_000, config.timeoutMs());
        assertEquals(2, config.maxRetries());
    }

    @Test
    void mapsTheSandboxEnvironmentToItsHost() {
        ResolvedConfig config = resolve(HighClientOptions.builder().environment(Environment.SANDBOX).build());
        assertEquals(Environments.get(Environment.SANDBOX).api(), config.baseUrl());
    }

    @Test
    void resolvesAWebsocketHostAlongsideTheApiHost() {
        assertEquals(Environments.get(Environment.PRODUCTION).ws(),
            resolve(HighClientOptions.builder().build()).wsBaseUrl());
        assertEquals(Environments.get(Environment.SANDBOX).ws(),
            resolve(HighClientOptions.builder().environment(Environment.SANDBOX).build()).wsBaseUrl());
    }

    /**
     * Pins the literal production feed host as a unit assertion rather than
     * ever dialling it: {@code openapi-feed.high.live} is not deployed yet, so
     * nothing in this suite may attempt a live connection to it — every
     * {@code HighFeed} test points at a local {@code WebSocketTestServer}
     * instead (see {@code HighFeedTest}), and this is the one place the real
     * default is checked at all.
     */
    @Test
    void theDefaultProductionFeedHostIsTheRealFeedHostLiteral() {
        assertEquals("wss://openapi-feed.high.live", Environments.get(Environment.PRODUCTION).ws());
        assertEquals("wss://openapi-feed.high.live",
            resolve(HighClientOptions.builder().build()).wsBaseUrl());
    }

    @Test
    void letsWsBaseUrlBeOverriddenWithoutTouchingBaseUrl() {
        ResolvedConfig config = resolve(HighClientOptions.builder().wsBaseUrl("ws://127.0.0.1:9001").build());
        assertEquals("ws://127.0.0.1:9001", config.wsBaseUrl());
        assertEquals(Environments.get(Environment.PRODUCTION).api(), config.baseUrl());
    }

    @Test
    void letsAnExplicitBaseUrlWinOverTheEnvironment() {
        ResolvedConfig config = resolve(HighClientOptions.builder()
            .environment(Environment.SANDBOX).baseUrl("http://localhost:8080").build());
        assertEquals("http://localhost:8080", config.baseUrl());
    }

    @Test
    void readsEnvironmentAndCredentialsFromEnvVarsWhenNotPassed() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build(), Map.of(
            "HIGH_ENVIRONMENT", "sandbox", "HIGH_API_KEY", "k", "HIGH_ACCESS_TOKEN", "t"));
        assertEquals(Environments.get(Environment.SANDBOX).api(), config.baseUrl());
        assertEquals("k", config.apiKey().orElseThrow());
        assertEquals("t", config.accessToken().orElseThrow());
    }

    @Test
    void prefersExplicitOptionsOverEnvVars() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().apiKey("explicit").build(),
            Map.of("HIGH_API_KEY", "from-env"));
        assertEquals("explicit", config.apiKey().orElseThrow());
    }

    @Test
    void rejectsAnUnknownEnvironmentAtConstructionNamingTheValidValues() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> Environment.fromString("staging", "options.environment"));
        assertTrue(error.getMessage().contains("staging"));
        assertTrue(error.getMessage().contains("production"));
        assertTrue(error.getMessage().contains("sandbox"));
    }

    @Test
    void defaultsLoggingToSilent() {
        assertEquals(LogLevel.SILENT, resolve(HighClientOptions.builder().build()).logLevel());
    }

    @Test
    void takesALogLevelFromOptionsOrEnvVarOptionsWinning() {
        assertEquals(LogLevel.DEBUG,
            resolve(HighClientOptions.builder().logLevel(LogLevel.DEBUG).build()).logLevel());
        assertEquals(LogLevel.WARN,
            resolve(HighClientOptions.builder().build(), Map.of("HIGH_LOG_LEVEL", "warn")).logLevel());
        assertEquals(LogLevel.INFO,
            resolve(HighClientOptions.builder().logLevel(LogLevel.INFO).build(),
                Map.of("HIGH_LOG_LEVEL", "warn")).logLevel());
    }

    @Test
    void rejectsAnUnknownHighEnvironmentValueToo() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> resolve(HighClientOptions.builder().build(), Map.of("HIGH_ENVIRONMENT", "prod")));
        assertTrue(error.getMessage().contains("prod"));
    }

    @Test
    void buildUrlJoinsBaseVersionAndPath() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build());
        assertEquals("https://openapi.high.live/v1/orders/list", config.buildUrl("/orders/list"));
    }

    @Test
    void buildUrlToleratesStraySlashesOnEveryPart() {
        ResolvedConfig config = resolve(HighClientOptions.builder()
            .baseUrl("https://h.example/").versionPath("/v2/").build());
        assertEquals("https://h.example/v2/orders", config.buildUrl("/orders"));
        assertEquals("https://h.example/v2/orders", config.buildUrl("orders"));
    }

    @Test
    void buildUrlSupportsABaseUrlThatAlreadyCarriesAPathPrefix() {
        ResolvedConfig config = resolve(HighClientOptions.builder().baseUrl("https://h.example/api/").build());
        assertEquals("https://h.example/api/v1/orders", config.buildUrl("/orders"));
    }

    @Test
    void buildUrlAllowsAnEmptyVersionPathForAnUnversionedHost() {
        ResolvedConfig config = resolve(HighClientOptions.builder().versionPath("").build());
        assertEquals("https://openapi.high.live/orders", config.buildUrl("/orders"));
    }

    // Finding 1 (Node review, replayed here): an explicit environment must beat
    // a stray env var.
    @Test
    void explicitEnvironmentBeatsHighBaseUrl() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().environment(Environment.SANDBOX).build(),
            Map.of("HIGH_BASE_URL", "https://openapi.high.live"));
        assertEquals(Environments.get(Environment.SANDBOX).api(), config.baseUrl());
    }

    @Test
    void explicitEnvironmentBeatsHighWsBaseUrlTooSoHostsCannotSplit() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().environment(Environment.SANDBOX).build(),
            Map.of("HIGH_BASE_URL", "https://openapi.high.live", "HIGH_WS_BASE_URL", "wss://openapi.high.live"));
        assertEquals(Environments.get(Environment.SANDBOX).api(), config.baseUrl());
        assertEquals(Environments.get(Environment.SANDBOX).ws(), config.wsBaseUrl());
    }

    @Test
    void highBaseUrlStillAppliesWhenNoEnvironmentIsGiven() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build(),
            Map.of("HIGH_BASE_URL", "http://localhost:9999"));
        assertEquals("http://localhost:9999", config.baseUrl());
    }

    @Test
    void anExplicitBaseUrlStillWinsOverEverything() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().environment(Environment.SANDBOX).baseUrl("http://localhost:1").build(),
            Map.of("HIGH_BASE_URL", "https://openapi.high.live"));
        assertEquals("http://localhost:1", config.baseUrl());
    }

    @Test
    void rejectsANegativeMaxRetriesRatherThanCrashingMidRequest() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> resolve(HighClientOptions.builder().maxRetries(-1).build()));
        assertTrue(error.getMessage().contains("maxRetries"));
    }

    @Test
    void rejectsANonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class,
            () -> resolve(HighClientOptions.builder().timeoutMs(0).build()));
        assertThrows(IllegalArgumentException.class,
            () -> resolve(HighClientOptions.builder().timeoutMs(-1).build()));
    }

    @Test
    void rejectsANonPositiveMaxRetryDelay() {
        assertThrows(IllegalArgumentException.class,
            () -> resolve(HighClientOptions.builder().maxRetryDelayMs(0).build()));
    }

    // The trap Java's own record toString() would otherwise fall into.
    @Test
    void credentialsAreAbsentFromToString() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().apiKey("SECRET-KEY").accessToken("SECRET-TOKEN").build());
        String printed = config.toString();
        assertFalse(printed.contains("SECRET-KEY"));
        assertFalse(printed.contains("SECRET-TOKEN"));
    }

    @Test
    void credentialsAreStillUsableDespiteBeingHiddenFromToString() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().apiKey("SECRET-KEY").accessToken("SECRET-TOKEN").build());
        assertEquals("SECRET-KEY", config.apiKey().orElseThrow());
        assertEquals("SECRET-TOKEN", config.accessToken().orElseThrow());
    }

    @Test
    void missingCredentialsResolveToEmpty() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build());
        assertTrue(config.apiKey().isEmpty());
        assertTrue(config.accessToken().isEmpty());
    }

    @Test
    void defaultsAllowedInstrumentHostsToTheKnownCdnHost() {
        ResolvedConfig config = resolve(HighClientOptions.builder().build());
        assertTrue(config.allowedInstrumentHosts().contains("high-space.blr1.cdn.digitaloceanspaces.com"));
    }

    @Test
    void letsAllowedInstrumentHostsBeOverridden() {
        ResolvedConfig config = resolve(
            HighClientOptions.builder().allowedInstrumentHosts(java.util.Set.of("127.0.0.1")).build());
        assertEquals(java.util.Set.of("127.0.0.1"), config.allowedInstrumentHosts());
    }
}
