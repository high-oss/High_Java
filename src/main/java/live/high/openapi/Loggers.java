// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds loggers and does the redaction their callers rely on.
 *
 * <p>Redaction is not optional, and it is why logging goes through this one
 * module. Headers are never logged at all (the caller of {@link
 * live.high.openapi.HighClient} never hands a header to a logger in the first
 * place); URLs and bodies are redacted here, by key name, at any nesting
 * depth.
 */
public final class Loggers {

    private Loggers() {}

    // Registers JavaTimeModule directly rather than reusing the generated
    // JSON class's mapper: Loggers is a foundational, model-agnostic module
    // (it redacts whatever object it is handed), so it stays decoupled from
    // generated/ rather than depending on it. A caller can legitimately log
    // any payload, including a generated model with an OffsetDateTime or
    // LocalDate field, so the module is still needed here too.
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * Query parameters that must never reach a log sink. {@code tOtp} is a live
     * second factor and the token parameters are bearer credentials.
     */
    private static final Set<String> SENSITIVE_PARAMS =
        Set.of("tOtp", "apiKey", "accessToken", "tokenId", "stepToken");

    private static final Pattern QUERY_PARAM = Pattern.compile("([?&])([^=&]+)=([^&]*)");

    /** Replaces sensitive query-parameter values with {@code REDACTED}. Never throws. */
    public static String redactUrl(String url) {
        Matcher matcher = QUERY_PARAM.matcher(url);
        StringBuilder result = new StringBuilder();
        boolean touched = false;
        int last = 0;
        while (matcher.find()) {
            String key = matcher.group(2);
            if (SENSITIVE_PARAMS.contains(key)) {
                result.append(url, last, matcher.start());
                result.append(matcher.group(1)).append(key).append('=').append("REDACTED");
                last = matcher.end();
                touched = true;
            }
        }
        if (!touched) return url;
        result.append(url, last, url.length());
        return result.toString();
    }

    /**
     * Object keys whose values must never be logged, whatever nesting they sit
     * at.
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "tOtp", "totp", "otp", "apiKey", "accessToken", "refreshToken",
        "tokenId", "stepToken", "password", "pin", "authorization");

    /** Above this, a logged payload is replaced by a note rather than printed. */
    private static final int MAX_LOGGED_JSON = 1500;

    /**
     * Deep-copies a payload for logging as a JSON string, masking
     * credential-shaped keys and replacing anything oversized with a note.
     * Primitives (and {@code null}) pass through untouched.
     */
    public static Object redactBody(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        JsonNode node;
        try {
            node = value instanceof JsonNode ? (JsonNode) value : MAPPER.valueToTree(value);
        } catch (RuntimeException e) {
            return "[unserialisable]";
        }
        if (!(node.isObject() || node.isArray())) return value;

        JsonNode masked = mask(node);
        String serialised;
        try {
            serialised = MAPPER.writeValueAsString(masked);
        } catch (Exception e) {
            return "[unserialisable]";
        }
        if (serialised.length() > MAX_LOGGED_JSON) {
            return "[truncated: " + serialised.length() + " bytes of JSON]";
        }
        return serialised;
    }

    private static JsonNode mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (SENSITIVE_KEYS.contains(field.getKey())) {
                    out.set(field.getKey(), TextNode.valueOf("REDACTED"));
                } else {
                    out.set(field.getKey(), mask(field.getValue()));
                }
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode item : node) out.add(mask(item));
            return out;
        }
        return node;
    }

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    /** {@code 2026-09-28T17:05:12.345Z DEBUG message} — sortable, and aligned in a terminal. */
    private static String format(LogLevel level, String message) {
        String ts = TIMESTAMP.format(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        String padded = String.format("%-5s", level.wireValue().toUpperCase(java.util.Locale.ROOT));
        return ts + " " + padded + " " + message;
    }

    /** Builds a logger that drops anything below {@code level}. */
    public static Logger create(LogLevel level, LogSink sink) {
        return new Logger() {
            @Override
            public boolean enabled(LogLevel want) {
                return want.rank <= level.rank;
            }

            @Override
            public void error(String message, Object detail) {
                if (enabled(LogLevel.ERROR)) sink.error(format(LogLevel.ERROR, message), detail);
            }

            @Override
            public void warn(String message, Object detail) {
                if (enabled(LogLevel.WARN)) sink.warn(format(LogLevel.WARN, message), detail);
            }

            @Override
            public void info(String message, Object detail) {
                if (enabled(LogLevel.INFO)) sink.info(format(LogLevel.INFO, message), detail);
            }

            @Override
            public void debug(String message, Object detail) {
                if (enabled(LogLevel.DEBUG)) sink.debug(format(LogLevel.DEBUG, message), detail);
            }
        };
    }

    /** The default sink: {@code System.err} for error/warn, {@code System.out} for info/debug. */
    public static LogSink consoleSink() {
        return new LogSink() {
            @Override
            public void error(String message, Object detail) {
                print(System.err, message, detail);
            }

            @Override
            public void warn(String message, Object detail) {
                print(System.err, message, detail);
            }

            @Override
            public void info(String message, Object detail) {
                print(System.out, message, detail);
            }

            @Override
            public void debug(String message, Object detail) {
                print(System.out, message, detail);
            }

            private void print(java.io.PrintStream out, String message, Object detail) {
                if (detail == null) out.println(message);
                else out.println(message + " " + detail);
            }
        };
    }
}
