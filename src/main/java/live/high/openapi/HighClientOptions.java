// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.net.http.HttpClient;
import java.util.Set;

/**
 * Options for {@link HighClient}, built via {@link #builder()}.
 *
 * <p>Every field is optional; {@link ResolvedConfig#resolve} applies defaults,
 * environment-variable fallbacks and validation once, at construction. This
 * type itself is an inert, immutable bag of "what was passed" — it does not
 * know about {@code HIGH_*} environment variables or defaults.
 */
public final class HighClientOptions {

    private final Environment environment;
    private final String baseUrl;
    private final String wsBaseUrl;
    private final String versionPath;
    private final String apiKey;
    private final String accessToken;
    private final Integer timeoutMs;
    private final Integer maxRetries;
    private final Integer maxRetryDelayMs;
    private final String userAgent;
    private final LogLevel logLevel;
    private final LogSink logSink;
    private final HttpClient httpClient;
    private final Set<String> allowedInstrumentHosts;

    private HighClientOptions(Builder b) {
        this.environment = b.environment;
        this.baseUrl = b.baseUrl;
        this.wsBaseUrl = b.wsBaseUrl;
        this.versionPath = b.versionPath;
        this.apiKey = b.apiKey;
        this.accessToken = b.accessToken;
        this.timeoutMs = b.timeoutMs;
        this.maxRetries = b.maxRetries;
        this.maxRetryDelayMs = b.maxRetryDelayMs;
        this.userAgent = b.userAgent;
        this.logLevel = b.logLevel;
        this.logSink = b.logSink;
        this.httpClient = b.httpClient;
        this.allowedInstrumentHosts = b.allowedInstrumentHosts;
    }

    Environment environment() {
        return environment;
    }

    String baseUrl() {
        return baseUrl;
    }

    String wsBaseUrl() {
        return wsBaseUrl;
    }

    String versionPath() {
        return versionPath;
    }

    String apiKey() {
        return apiKey;
    }

    String accessToken() {
        return accessToken;
    }

    Integer timeoutMs() {
        return timeoutMs;
    }

    Integer maxRetries() {
        return maxRetries;
    }

    Integer maxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    String userAgent() {
        return userAgent;
    }

    LogLevel logLevel() {
        return logLevel;
    }

    LogSink logSink() {
        return logSink;
    }

    HttpClient httpClient() {
        return httpClient;
    }

    Set<String> allowedInstrumentHosts() {
        return allowedInstrumentHosts;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link HighClientOptions}. */
    public static final class Builder {
        private Environment environment;
        private String baseUrl;
        private String wsBaseUrl;
        private String versionPath;
        private String apiKey;
        private String accessToken;
        private Integer timeoutMs;
        private Integer maxRetries;
        private Integer maxRetryDelayMs;
        private String userAgent;
        private LogLevel logLevel;
        private LogSink logSink;
        private HttpClient httpClient;
        private Set<String> allowedInstrumentHosts;

        private Builder() {}

        /** Which HIGH environment to talk to. Ignored when {@link #baseUrl} is set. Default: production. */
        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        /** Overrides {@link #environment} entirely. Use for a staging host or a local mock. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Datafeed socket host. Reserved for the feed client; overrides {@link #environment}. */
        public Builder wsBaseUrl(String wsBaseUrl) {
            this.wsBaseUrl = wsBaseUrl;
            return this;
        }

        /** API version segment between the host and the operation path. Default: {@code "v1"}. */
        public Builder versionPath(String versionPath) {
            this.versionPath = versionPath;
            return this;
        }

        /** Sent as {@code x-api-key}. Required by {@code auth.generateAccessToken}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Sent as {@code Authorization: Bearer}. Required by the other 23 operations. */
        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        /** Per-request timeout, covering the whole exchange (headers and body). Default: 30000. */
        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /** Retries for idempotent reads only. Default: 2. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Ceiling on a single retry delay. A {@code Retry-After} longer than this
         * is not waited out — the SDK gives up and throws instead. Default: 30000.
         */
        public Builder maxRetryDelayMs(int maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return this;
        }

        /** Appended to the SDK's own User-Agent. */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * How much the SDK prints. Each level prints itself and everything more
         * severe. Default: {@code silent}. Credentials and TOTPs are redacted at
         * every level.
         */
        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        /** Where log lines go. Default: the console. */
        public Builder logSink(LogSink logSink) {
            this.logSink = logSink;
            return this;
        }

        /** Transport override, for tests or a proxy-aware client. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Hosts {@code instruments()} is allowed to download the instrument-list
         * CSV from, after resolving the download URL the manifest names. Default:
         * the one CDN host the manifest normally points at. A URL whose host is
         * not in this set (or whose scheme is not {@code https}) is refused
         * before any request is made — the file is served by a third-party CDN,
         * and this is what keeps a compromised or malformed manifest from
         * redirecting the SDK somewhere unexpected.
         */
        public Builder allowedInstrumentHosts(Set<String> allowedInstrumentHosts) {
            this.allowedInstrumentHosts = allowedInstrumentHosts;
            return this;
        }

        public HighClientOptions build() {
            return new HighClientOptions(this);
        }
    }
}
