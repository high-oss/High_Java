// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Settles where requests go and what they carry, once, at client
 * construction. Never re-resolved: a new access token means a new client.
 *
 * <p>Resolution order: an explicit {@code baseUrl} wins outright; then an
 * explicit {@code environment} (which also suppresses the environment
 * variables — see the class-level note in {@link Environments}); then {@code
 * HIGH_BASE_URL} / {@code HIGH_ENVIRONMENT}; then production. An unknown
 * environment or log level, or an invalid timeout/retry value, fails here
 * rather than at request time.
 *
 * <p>This class deliberately is not a {@code record}: a record's generated
 * {@code toString()} prints every component, which is exactly the trap for a
 * type that carries an API key and a bearer token. {@link #toString()} is
 * hand-written and omits both entirely.
 */
public final class ResolvedConfig {

    private static final String DEFAULT_USER_AGENT = "high-sdk-java/0.0.1";

    /**
     * The one host {@code InstrumentFile.url} normally points at. Kept here,
     * next to the other defaults, so {@code allowedInstrumentHosts} has
     * somewhere to fall back to without {@code InstrumentsResource} needing
     * its own notion of "default config".
     */
    private static final Set<String> DEFAULT_ALLOWED_INSTRUMENT_HOSTS =
        Set.of("high-space.blr1.cdn.digitaloceanspaces.com");

    private final String baseUrl;
    private final String wsBaseUrl;
    private final String versionPath;
    private final int timeoutMs;
    private final int maxRetries;
    private final int maxRetryDelayMs;
    private final String userAgent;
    private final LogLevel logLevel;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Set<String> allowedInstrumentHosts;

    // Deliberately excluded from toString(). See the class-level note.
    private final String apiKey;
    private final String accessToken;

    private ResolvedConfig(
        String baseUrl, String wsBaseUrl, String versionPath,
        int timeoutMs, int maxRetries, int maxRetryDelayMs,
        String userAgent, LogLevel logLevel, Logger logger, HttpClient httpClient,
        Set<String> allowedInstrumentHosts, String apiKey, String accessToken
    ) {
        this.baseUrl = baseUrl;
        this.wsBaseUrl = wsBaseUrl;
        this.versionPath = versionPath;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.maxRetryDelayMs = maxRetryDelayMs;
        this.userAgent = userAgent;
        this.logLevel = logLevel;
        this.logger = logger;
        this.httpClient = httpClient;
        this.allowedInstrumentHosts = allowedInstrumentHosts;
        this.apiKey = apiKey;
        this.accessToken = accessToken;
    }

    public static ResolvedConfig resolve(HighClientOptions options) {
        return resolve(options, System.getenv());
    }

    public static ResolvedConfig resolve(HighClientOptions options, Map<String, String> env) {
        // Settle which environment's hosts apply, then let explicit URLs override
        // either one independently — a developer may point the REST client at a
        // local mock while still using the real feed, or the reverse.
        boolean explicitEnvironment = options.environment() != null;
        Environment environment = explicitEnvironment ? options.environment() : Environment.PRODUCTION;
        if (!explicitEnvironment) {
            String fromEnv = env.get("HIGH_ENVIRONMENT");
            if (fromEnv != null) environment = Environment.fromString(fromEnv, "HIGH_ENVIRONMENT");
        }
        Environments.Hosts hosts = Environments.get(environment);

        // An explicit `environment` suppresses the env-var hosts. Otherwise a
        // HIGH_BASE_URL left in a shell profile from a mock-server session would
        // silently route a client written as `environment: sandbox` at
        // production, and could leave the REST host on one environment and the
        // socket host on the other. Only an explicit URL option outranks an
        // explicit environment.
        String baseUrl = options.baseUrl() != null ? options.baseUrl()
            : (explicitEnvironment ? hosts.api() : env.getOrDefault("HIGH_BASE_URL", hosts.api()));
        String wsBaseUrl = options.wsBaseUrl() != null ? options.wsBaseUrl()
            : (explicitEnvironment ? hosts.ws() : env.getOrDefault("HIGH_WS_BASE_URL", hosts.ws()));

        String userAgent = options.userAgent() != null
            ? DEFAULT_USER_AGENT + " " + options.userAgent()
            : DEFAULT_USER_AGENT;

        LogLevel logLevel = LogLevel.SILENT;
        if (options.logLevel() != null) {
            logLevel = options.logLevel();
        } else {
            String fromEnv = env.get("HIGH_LOG_LEVEL");
            if (fromEnv != null) logLevel = LogLevel.fromString(fromEnv, "HIGH_LOG_LEVEL");
        }

        // Validated here rather than mid-request, so a bad number cannot surface
        // as a raw exception from deep inside the retry loop.
        int timeoutMs = requirePositive(options.timeoutMs() != null ? options.timeoutMs() : 30_000, "timeoutMs");
        int maxRetries = requireNonNegative(options.maxRetries() != null ? options.maxRetries() : 2, "maxRetries");
        int maxRetryDelayMs = requirePositive(
            options.maxRetryDelayMs() != null ? options.maxRetryDelayMs() : 30_000, "maxRetryDelayMs");

        String rawVersionPath = options.versionPath() != null ? options.versionPath() : "v1";

        LogSink sink = options.logSink() != null ? options.logSink() : Loggers.consoleSink();
        Logger logger = Loggers.create(logLevel, sink);

        HttpClient httpClient = options.httpClient() != null ? options.httpClient() : HttpClient.newHttpClient();

        Set<String> allowedInstrumentHosts = options.allowedInstrumentHosts() != null
            ? Set.copyOf(options.allowedInstrumentHosts())
            : DEFAULT_ALLOWED_INSTRUMENT_HOSTS;

        String apiKey = options.apiKey() != null ? options.apiKey() : env.get("HIGH_API_KEY");
        String accessToken = options.accessToken() != null ? options.accessToken() : env.get("HIGH_ACCESS_TOKEN");

        return new ResolvedConfig(
            stripTrailingSlashes(baseUrl), stripTrailingSlashes(wsBaseUrl), trimSlashes(rawVersionPath),
            timeoutMs, maxRetries, maxRetryDelayMs, userAgent, logLevel, logger, httpClient,
            allowedInstrumentHosts, apiKey, accessToken);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive number, received " + value + ".");
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative integer, received " + value + ".");
        }
        return value;
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') start++;
        while (end > start && value.charAt(end - 1) == '/') end--;
        return value.substring(start, end);
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    /** {@code baseUrl} + {@code versionPath} + operation path, with no doubled or missing slashes. */
    public String buildUrl(String path) {
        return Arrays.stream(new String[] {baseUrl, versionPath, trimSlashes(path)})
            .filter(segment -> !segment.isEmpty())
            .reduce((a, b) -> a + "/" + b)
            .orElse("");
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Reserved for the datafeed client. Unused by the REST resources. */
    public String wsBaseUrl() {
        return wsBaseUrl;
    }

    public String versionPath() {
        return versionPath;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int maxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public String userAgent() {
        return userAgent;
    }

    public LogLevel logLevel() {
        return logLevel;
    }

    /** Ready to use; already honours {@link #logLevel()}. */
    public Logger logger() {
        return logger;
    }

    public HttpClient httpClient() {
        return httpClient;
    }

    /** Hosts {@code instruments()} may download the instrument-list CSV from. See {@code HighClientOptions.allowedInstrumentHosts}. */
    public Set<String> allowedInstrumentHosts() {
        return allowedInstrumentHosts;
    }

    public Optional<String> apiKey() {
        return Optional.ofNullable(apiKey);
    }

    public Optional<String> accessToken() {
        return Optional.ofNullable(accessToken);
    }

    @Override
    public String toString() {
        // Deliberately omits apiKey and accessToken. See the class-level note —
        // this is the whole reason ResolvedConfig is not a record.
        return "ResolvedConfig{"
            + "baseUrl='" + baseUrl + '\''
            + ", wsBaseUrl='" + wsBaseUrl + '\''
            + ", versionPath='" + versionPath + '\''
            + ", timeoutMs=" + timeoutMs
            + ", maxRetries=" + maxRetries
            + ", maxRetryDelayMs=" + maxRetryDelayMs
            + ", userAgent='" + userAgent + '\''
            + ", logLevel=" + logLevel
            + ", allowedInstrumentHosts=" + allowedInstrumentHosts
            + ", apiKey=" + (apiKey != null ? "<set>" : "<absent>")
            + ", accessToken=" + (accessToken != null ? "<set>" : "<absent>")
            + '}';
    }
}
