// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends one operation and returns its unwrapped {@code data}.
 *
 * <p><b>Cancellation.</b> {@code java.net.http} has no {@code AbortSignal}
 * equivalent, so cancellation is achieved the idiomatic Java way: through the
 * {@link CompletableFuture} that {@link #sendAsync} returns. Calling {@code
 * cancel(true)} on it cancels whichever stage is currently active — the
 * in-flight HTTP exchange, or a pending retry delay — never lets a cancelled
 * call go on to issue its next attempt, and never converts into a timeout.
 * {@link #send} is the blocking form, built on the same async path, so there
 * is exactly one implementation of retry/timeout/redaction behaviour.
 *
 * <p><b>Timeout.</b> One timer per attempt covers the whole exchange, headers
 * and body: {@link HttpResponse.BodyHandlers#ofString()} does not complete
 * its future until the entire body has been read, so cancelling that future
 * when the timer fires bounds header wait and body read alike.
 *
 * <p><b>Retries.</b> Idempotent reads only ({@code GET}/{@code HEAD}), on
 * 429 and 5xx, honouring {@code Retry-After} in both its forms, with
 * exponential backoff otherwise, and a ceiling above which the SDK gives up
 * rather than blocking the caller.
 */
public final class Transport {

    private Transport() {}

    public enum AuthKind { BEARER, API_KEY }

    /** One operation to send. {@code query} values are already stringified; nulls are skipped by the caller. */
    public record RequestSpec(
        String method, String path, AuthKind auth, Map<String, String> query, Object body
    ) {}

    // Reuses the generated JSON class's ObjectMapper (JavaTimeModule,
    // JsonNullableModule, enum (de)serialization via the generator's own
    // conventions) rather than a bare `new ObjectMapper()` — the generated
    // models carry OffsetDateTime/LocalDate fields (AccessToken.expiresAt,
    // MarketStatus.date, Expiry.expiry, …) that fail without it.
    private static final ObjectMapper MAPPER = live.high.openapi.generated.JSON.getDefault().getMapper();

    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD");

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "high-sdk-transport");
        t.setDaemon(true);
        return t;
    });

    /** Blocking form of {@link #sendAsync}. Throws {@link HighApiError} for every failure. */
    public static <T> T send(ResolvedConfig config, RequestSpec spec, TypeReference<T> responseType) {
        try {
            return sendAsync(config, spec, responseType).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HighApiError highApiError) throw highApiError;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new HighApiError("Request failed: " + (cause == null ? e.getMessage() : cause.getMessage()), 0, cause);
        }
    }

    /**
     * A tiny hook so {@link #cancel} can reach whichever stage (HTTP call or
     * retry-delay timer) is currently active, since {@link CompletableFuture}
     * does not propagate cancellation to the futures it was chained from.
     */
    private static final class CancellableFuture<T> extends CompletableFuture<T> {
        private volatile Runnable onCancel;

        void setCancelHook(Runnable hook) {
            this.onCancel = hook;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // Mark this future cancelled FIRST, then tear down whichever stage is
            // active. Cancelling the inner stage (the HTTP call, or a retry-delay
            // timer) can complete its own future synchronously on this thread,
            // which runs completeAttempt() re-entrantly before cancel() returns —
            // if `this` were not already done by then, completeAttempt() would
            // win the race and complete `this` with a HighApiError instead of
            // leaving it cancelled, which is exactly the bug this ordering avoids.
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            Runnable hook = onCancel;
            if (hook != null) hook.run();
            return cancelled;
        }
    }

    public static <T> CompletableFuture<T> sendAsync(ResolvedConfig config, RequestSpec spec, TypeReference<T> responseType) {
        CancellableFuture<T> result = new CancellableFuture<>();
        try {
            Map<String, String> headers = headersFor(config, spec);
            String url = urlFor(config, spec);
            boolean retryable = IDEMPOTENT.contains(spec.method().toUpperCase(java.util.Locale.ROOT));
            int maxAttempts = retryable ? config.maxRetries() + 1 : 1;
            attempt(config, spec, url, headers, 0, maxAttempts, retryable, responseType, result);
        } catch (HighApiError e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private static Map<String, String> headersFor(ResolvedConfig config, RequestSpec spec) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("accept", "application/json");
        headers.put("user-agent", config.userAgent());
        if (spec.auth() == AuthKind.BEARER) {
            String token = config.accessToken().orElseThrow(() -> new HighApiError(
                "This operation needs an accessToken. Pass it to the client, or set HIGH_ACCESS_TOKEN.", 0));
            headers.put("authorization", "Bearer " + token);
        } else {
            String key = config.apiKey().orElseThrow(() -> new HighApiError(
                "This operation needs an apiKey. Pass it to the client, or set HIGH_API_KEY.", 0));
            headers.put("x-api-key", key);
        }
        if (spec.body() != null) headers.put("content-type", "application/json");
        return headers;
    }

    private static String urlFor(ResolvedConfig config, RequestSpec spec) {
        String base = config.buildUrl(spec.path());
        if (spec.query() == null || spec.query().isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : spec.query().entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                .append('=')
                .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static <T> void attempt(
        ResolvedConfig config, RequestSpec spec, String url, Map<String, String> headers,
        int index, int maxAttempts, boolean retryable, TypeReference<T> responseType,
        CancellableFuture<T> result
    ) {
        if (result.isDone()) return;

        String bodyJson = null;
        if (spec.body() != null) {
            try {
                bodyJson = MAPPER.writeValueAsString(spec.body());
            } catch (Exception e) {
                result.completeExceptionally(new HighApiError("Failed to serialise the request body: " + e.getMessage(), 0, e));
                return;
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
            .method(spec.method().toUpperCase(java.util.Locale.ROOT),
                bodyJson == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
        headers.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();

        Logger logger = config.logger();
        logger.debug("HIGH -> " + spec.method().toUpperCase(java.util.Locale.ROOT) + " " + Loggers.redactUrl(url),
            bodyJson == null ? null : Map.of("body", Loggers.redactBody(spec.body())));
        logger.info("HIGH " + spec.method().toUpperCase(java.util.Locale.ROOT) + " " + Loggers.redactUrl(url), null);

        long startedAt = System.currentTimeMillis();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        CompletableFuture<HttpResponse<String>> httpFuture =
            config.httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());

        ScheduledFuture<?> timeoutTask = SCHEDULER.schedule(() -> {
            timedOut.set(true);
            httpFuture.cancel(true);
        }, config.timeoutMs(), TimeUnit.MILLISECONDS);

        result.setCancelHook(() -> {
            timeoutTask.cancel(false);
            httpFuture.cancel(true);
        });

        httpFuture.whenComplete((response, throwable) -> {
            try {
                completeAttempt(config, spec, url, headers, index, maxAttempts, retryable, responseType, result,
                    logger, startedAt, timedOut, timeoutTask, response, throwable);
            } catch (Throwable unexpected) {
                // Nothing thrown on this callback's thread is otherwise observed by
                // anyone — CompletableFuture#whenComplete's own exception handling
                // only affects the (unused) future it returns. Left unguarded, a bug
                // here would silently abandon `result`, forever unresolved: a hang
                // indistinguishable from the network actually being down, except
                // that no timeout ever rescues it either. "Nothing else escapes the
                // SDK" (contract §4) means this too becomes a HighApiError.
                if (!result.isDone()) {
                    result.completeExceptionally(new HighApiError(
                        "Internal error handling the response: " + unexpected.getMessage(), 0, unexpected));
                }
            }
        });
    }

    private static <T> void completeAttempt(
        ResolvedConfig config, RequestSpec spec, String url, Map<String, String> headers,
        int index, int maxAttempts, boolean retryable, TypeReference<T> responseType,
        CancellableFuture<T> result, Logger logger, long startedAt, AtomicBoolean timedOut,
        ScheduledFuture<?> timeoutTask, HttpResponse<String> response, Throwable throwable
    ) {
        timeoutTask.cancel(false);
        if (result.isDone()) return; // cancelled meanwhile — never retried, never treated as a timeout

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            if (timedOut.get()) {
                logger.error("HIGH <- timeout after " + config.timeoutMs() + "ms " + Loggers.redactUrl(url), null);
                result.completeExceptionally(
                    new HighApiError("Request timed out after " + config.timeoutMs() + "ms", 0, cause));
            } else {
                logger.error("HIGH <- transport failure " + Loggers.redactUrl(url), cause.getMessage());
                result.completeExceptionally(new HighApiError("Request failed: " + cause.getMessage(), 0, cause));
            }
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        Parsed parsed = parse(response);

        logger.debug("HIGH <- " + response.statusCode() + " in " + elapsedMs + "ms " + Loggers.redactUrl(url),
            detail("requestId", parsed.requestId,
                "body", Loggers.redactBody(parsed.parseFailed ? truncate(response.body()) : parsed.tree)));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            HighApiError error = HighApiError.fromResponse(
                response.statusCode(), parsed.parseFailed ? null : parsed.tree, response.body());
            logger.error("HIGH <- " + response.statusCode() + " " + (error.code() == null ? "no code" : error.code())
                    + " in " + elapsedMs + "ms " + Loggers.redactUrl(url),
                detail("requestId", error.requestId()));

            Long retryAfter = retryAfterMillis(response.headers().firstValue("retry-after").orElse(null), System.currentTimeMillis());
            boolean lastAttempt = index == maxAttempts - 1;
            if (!retryable || !RETRYABLE.contains(response.statusCode()) || lastAttempt) {
                result.completeExceptionally(error);
                return;
            }

            long backoff = (long) (250 * Math.pow(2, index)) + (long) (Math.random() * 100);
            long delay = retryAfter != null ? retryAfter : backoff;

            if (delay > config.maxRetryDelayMs()) {
                logger.warn("HIGH giving up: server asked for " + delay + "ms, over the "
                    + config.maxRetryDelayMs() + "ms ceiling " + Loggers.redactUrl(url), null);
                result.completeExceptionally(error);
                return;
            }

            logger.warn("HIGH retry " + (index + 1) + "/" + (maxAttempts - 1) + " after " + delay
                + "ms (HTTP " + response.statusCode() + ") " + Loggers.redactUrl(url), null);

            ScheduledFuture<?> delayTask = SCHEDULER.schedule(
                () -> attempt(config, spec, url, headers, index + 1, maxAttempts, retryable, responseType, result),
                delay, TimeUnit.MILLISECONDS);
            result.setCancelHook(() -> delayTask.cancel(false));
            return;
        }

        if (parsed.parseFailed) {
            result.completeExceptionally(new HighApiError(
                "Expected JSON but the response was not parseable", response.statusCode(), response.body()));
            return;
        }

        JsonNode dataNode = parsed.tree == null ? null : parsed.tree.get("data");
        if (dataNode == null || dataNode.isNull()) {
            result.complete(null);
            return;
        }
        try {
            result.complete(MAPPER.convertValue(dataNode, responseType));
        } catch (Exception e) {
            result.completeExceptionally(new HighApiError("Failed to parse the response body: " + e.getMessage(),
                response.statusCode(), e));
        }
    }

    /** Builds a small map for structured log detail, tolerating {@code null} values (unlike {@link Map#of}). */
    private static Map<String, Object> detail(Object... keyValuePairs) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put((String) keyValuePairs[i], keyValuePairs[i + 1] == null ? "" : keyValuePairs[i + 1]);
        }
        return map;
    }

    private record Parsed(JsonNode tree, boolean parseFailed, String requestId) {}

    private static Parsed parse(HttpResponse<String> response) {
        String text = response.body();
        if (text == null || text.isEmpty()) return new Parsed(null, false, null);
        try {
            JsonNode tree = MAPPER.readTree(text);
            String requestId = tree.hasNonNull("requestId") ? tree.get("requestId").asText() : null;
            return new Parsed(tree, false, requestId);
        } catch (Exception e) {
            return new Parsed(null, true, null);
        }
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        Set<Throwable> seen = new HashSet<>();
        while ((cur instanceof CompletionException || cur instanceof java.util.concurrent.ExecutionException)
            && cur.getCause() != null && seen.add(cur)) {
            cur = cur.getCause();
        }
        return cur;
    }

    /**
     * Parses a {@code Retry-After} header: either a delay in seconds or an
     * HTTP-date. Never returns a negative or non-numeric delay.
     */
    public static Long retryAfterMillis(String header, long nowMillis) {
        if (header == null || header.isEmpty()) return null;
        try {
            double seconds = Double.parseDouble(header);
            if (seconds >= 0) return Math.round(seconds * 1000);
        } catch (NumberFormatException ignored) {
            // fall through to the HTTP-date form
        }
        try {
            Instant at = DateTimeFormatter.RFC_1123_DATE_TIME.parse(header, Instant::from);
            return Math.max(0, at.toEpochMilli() - nowMillis);
        } catch (Exception ignored) {
            return null;
        }
    }
}
