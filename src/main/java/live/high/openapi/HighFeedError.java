// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.util.Locale;

/**
 * Every failure {@link HighFeed} surfaces that is not a plain programming
 * error ({@link IllegalStateException} for calling it out of order, {@link
 * IllegalArgumentException} for a bad argument) — an auth-gate rejection, an
 * unsupported or mistranslated scrip key, or a transport failure that
 * exhausted its retry budget.
 *
 * <p>Unchecked, matching {@link HighApiError} for the same reason (see its
 * Javadoc): a checked exception here would force a {@code throws} clause
 * through every listener callback and reconnect path.
 *
 * <p><b>{@link #reason()} is not derived from a confirmed {@code stCode}.</b>
 * Datafeed plan Phase 0 records that the gateway's exact {@code stCode} for a
 * missing data plan is not yet settled ("Needed to finish this: the exact
 * stCode our gateway returns for a missing data plan"), and warns that an
 * unrecognised {@code NotOk} must surface with its code and message intact
 * rather than being flattened. Guessing a numeric code here would risk
 * silently mis-classifying a real rejection reason once the true code turns
 * out to differ. Instead, {@link #authRejected} classifies by keyword in
 * {@code msg} (falling back to {@link Reason#OTHER} without losing {@link
 * #stCode()} or {@link #serverMessage()}) for the two cases a caller can act
 * on, and treats the one confirmed code — 11002, "invalid field count" — as
 * {@link Reason#MALFORMED_REQUEST} regardless of wording. When the real code
 * is confirmed, that single classification method is the only place to
 * change.
 */
public final class HighFeedError extends RuntimeException {

    /** Which of the named, actionable cases this failure falls into. */
    public enum Reason {
        /** {@code stat:"NotOk"} — the account has no active Data API subscription. Never retried. */
        NO_DATA_PLAN,
        /** {@code stat:"NotOk"} — the access token is missing, expired, or otherwise invalid. Never retried. */
        INVALID_TOKEN,
        /** {@code stat:"NotOk"}, {@code stCode} 11002 — the auth frame itself was malformed. Never retried. */
        MALFORMED_REQUEST,
        /** {@code stat:"NotOk"} for a reason this SDK does not (yet) name. {@link #stCode()} and {@link #serverMessage()} carry the rest. Never retried. */
        OTHER,
        /** A scrip key this connection refuses to translate — unknown prefix, MCX spot, an index absent from the table, or an index method given a non-index key (or vice versa). */
        UNSUPPORTED_SCRIP,
        /** A scrip key the scrip master itself maps to more than one index (one token shared by two index names). Never guessed at — see {@code live.high.openapi.feed.IndexFeedMap#AMBIGUOUS}. */
        AMBIGUOUS_SCRIP,
        /** A subscription would exceed {@code maxScripPerConn} or a single request would exceed {@code maxScripPerReq}. */
        LIMIT_EXCEEDED,
        /** The socket itself — connect, handshake, or an in-flight exchange failed. Reconnected with backoff; this is not that retry failing, it is a single attempt's outcome. */
        TRANSPORT
    }

    private final Reason reason;
    private final Integer stCode;
    private final String serverMessage;

    private HighFeedError(String message, Reason reason, Integer stCode, String serverMessage, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.stCode = stCode;
        this.serverMessage = serverMessage;
    }

    public Reason reason() {
        return reason;
    }

    /** The auth ack's {@code stCode}, when this came from one. {@code null} otherwise. */
    public Integer stCode() {
        return stCode;
    }

    /** The auth ack's {@code msg}, when this came from one. {@code null} otherwise. */
    public String serverMessage() {
        return serverMessage;
    }

    /** Builds the error for a {@code stat:"NotOk"} auth acknowledgement. See the class Javadoc on how {@link Reason} is chosen. */
    public static HighFeedError authRejected(Integer stCode, String msg) {
        Reason reason = classifyAuthRejection(stCode, msg);
        StringBuilder message = new StringBuilder("HIGH datafeed authentication was rejected");
        if (stCode != null) message.append(" (stCode ").append(stCode).append(')');
        message.append(": ").append(msg == null ? "no message given" : msg);
        return new HighFeedError(message.toString(), reason, stCode, msg, null);
    }

    private static Reason classifyAuthRejection(Integer stCode, String msg) {
        if (stCode != null && stCode == 11002) return Reason.MALFORMED_REQUEST;
        String m = msg == null ? "" : msg.toLowerCase(Locale.ROOT);
        if (m.contains("plan") || m.contains("subscription") || m.contains("subscribe")) return Reason.NO_DATA_PLAN;
        if (m.contains("token") || m.contains("session") || m.contains("expired") || m.contains("auth")) {
            return Reason.INVALID_TOKEN;
        }
        return Reason.OTHER;
    }

    public static HighFeedError unsupportedScrip(String message) {
        return new HighFeedError(message, Reason.UNSUPPORTED_SCRIP, null, null, null);
    }

    public static HighFeedError ambiguousScrip(String message) {
        return new HighFeedError(message, Reason.AMBIGUOUS_SCRIP, null, null, null);
    }

    public static HighFeedError limitExceeded(String message) {
        return new HighFeedError(message, Reason.LIMIT_EXCEEDED, null, null, null);
    }

    public static HighFeedError transport(String message, Throwable cause) {
        return new HighFeedError(message, Reason.TRANSPORT, null, null, cause);
    }
}
