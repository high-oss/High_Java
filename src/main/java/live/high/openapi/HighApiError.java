// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every failure the SDK surfaces — transport, timeout, or an API error
 * response. Nothing else escapes the SDK: no raw {@code HttpClient}
 * exception, no JSON parse error.
 *
 * <p>Unchecked on purpose. A checked exception here would force a {@code
 * throws} clause through every resource method, through the builder-style
 * facade, and through every lambda a caller wraps a call in (a retry loop, a
 * stream pipeline, a {@code CompletableFuture} chain) — and {@code
 * HttpClient} itself already throws unchecked from its async path, so
 * unchecked is the idiom the rest of the surface has to compose with anyway.
 */
public final class HighApiError extends RuntimeException {

    private final int status;
    private final String code;
    private final String requestId;
    private final List<String> messages;
    private final Object body;

    public HighApiError(String message, int status) {
        this(message, status, null, null, null, null);
    }

    public HighApiError(String message, int status, Object body) {
        this(message, status, null, null, null, body);
    }

    public HighApiError(
        String message, int status, String code, String requestId, List<String> messages, Object body
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.messages = messages == null ? null : List.copyOf(messages);
        this.body = body;
    }

    /** HTTP status, or 0 for a transport failure or timeout. */
    public int status() {
        return status;
    }

    /**
     * An open string — the documented catalogue is {@link ErrorCodes#ALL}, but
     * the gateway emits codes outside it, so this is never validated against a
     * closed set.
     */
    public String code() {
        return code;
    }

    /** Quote this in support tickets. May be {@code null} for a transport failure. */
    public String requestId() {
        return requestId;
    }

    /** Every message the API returned; validation errors return several. May be {@code null}. */
    public List<String> messages() {
        return messages;
    }

    /** The parsed body (a Jackson {@link JsonNode}) or raw text, when there was one. */
    public Object body() {
        return body;
    }

    /**
     * Builds the error for a non-2xx response. {@code body} is the parsed JSON
     * when the response had any; {@code fallbackText} is the raw text when it
     * did not — a proxy or load balancer failure returns HTML, and losing that
     * to a JSON parse error would hide the status the caller needs.
     */
    public static HighApiError fromResponse(int status, JsonNode body, String fallbackText) {
        JsonNode record = (body != null && body.isObject()) ? body : null;
        List<String> messages = record == null ? null : messagesOf(record.get("message"));

        String snippet = fallbackText == null ? null : trim200(fallbackText.strip());

        String message;
        if (messages != null && !messages.isEmpty()) {
            message = String.join("; ", messages);
        } else if (snippet != null) {
            message = "HTTP " + status + ": " + snippet;
        } else {
            message = "HTTP " + status;
        }

        String code = (record != null && record.hasNonNull("code")) ? record.get("code").asText() : null;
        String requestId = (record != null && record.hasNonNull("requestId"))
            ? record.get("requestId").asText() : null;

        Object bodyOut = body != null ? body : fallbackText;

        return new HighApiError(message, status, code, requestId, messages, bodyOut);
    }

    private static List<String> messagesOf(JsonNode messageNode) {
        if (messageNode == null || messageNode.isNull()) return null;
        if (messageNode.isTextual()) return List.of(messageNode.asText());
        if (messageNode.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode item : messageNode) {
                if (item.isTextual()) out.add(item.asText());
            }
            return out.isEmpty() ? null : Collections.unmodifiableList(out);
        }
        return null;
    }

    private static String trim200(String value) {
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
