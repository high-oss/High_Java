// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.util.List;

/**
 * The documented error catalogue, from the spec's {@code
 * Error.code['x-error-codes']}.
 *
 * <p>{@link HighApiError#code()} is a plain {@code String}, not this set,
 * deliberately: the gateway defines far more codes than the catalogue
 * documents and passes most through unchanged, so treating this as a closed
 * enum would turn a recoverable business error into a deserialisation crash.
 * A {@code SpecSyncTest} asserts {@link #ALL} still matches the pinned spec.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String INVALID_API_KEY = "INVALID_API_KEY";
    public static final String INVALID_CONSENT = "INVALID_CONSENT";
    public static final String INVALID_DATE = "INVALID_DATE";
    public static final String INVALID_SCRIP = "INVALID_SCRIP";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String INVALID_TOKEN_ID = "INVALID_TOKEN_ID";
    public static final String INVALID_TOTP = "INVALID_TOTP";
    public static final String ORDER_DETAILS_INVALID = "ORDER_DETAILS_INVALID";
    public static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String ORDER_STATUS_UNKNOWN = "ORDER_STATUS_UNKNOWN";
    public static final String OWNER_MISMATCH = "OWNER_MISMATCH";
    public static final String POSITION_CONVERT_FAILED = "POSITION_CONVERT_FAILED";
    public static final String POSITION_NOT_EXITABLE = "POSITION_NOT_EXITABLE";
    public static final String POSITION_SQUARE_OFF_FAILED = "POSITION_SQUARE_OFF_FAILED";
    public static final String QUANTITY_NOT_IN_LOTS = "QUANTITY_NOT_IN_LOTS";
    public static final String REDIRECT_URL_NOT_CONFIGURED = "REDIRECT_URL_NOT_CONFIGURED";
    public static final String SANDBOX_TOKEN_NOT_ALLOWED = "SANDBOX_TOKEN_NOT_ALLOWED";
    public static final String SCRIP_NOT_FOUND = "SCRIP_NOT_FOUND";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String SESSION_NOT_GENERATED = "SESSION_NOT_GENERATED";
    public static final String STATIC_IP_MISMATCH = "STATIC_IP_MISMATCH";
    public static final String STATIC_IP_MISSING = "STATIC_IP_MISSING";
    public static final String TOTP_NOT_ENABLED = "TOTP_NOT_ENABLED";
    public static final String UNHANDLED_ERROR = "UNHANDLED_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String WRONG_TOKEN_AUDIENCE = "WRONG_TOKEN_AUDIENCE";

    /** Every documented code, in the spec's own order. */
    public static final List<String> ALL = List.of(
        INVALID_API_KEY, INVALID_CONSENT, INVALID_DATE, INVALID_SCRIP, INVALID_TOKEN,
        INVALID_TOKEN_ID, INVALID_TOTP, ORDER_DETAILS_INVALID, ORDER_NOT_CANCELLABLE,
        ORDER_NOT_FOUND, ORDER_REJECTED, ORDER_STATUS_UNKNOWN, OWNER_MISMATCH,
        POSITION_CONVERT_FAILED, POSITION_NOT_EXITABLE, POSITION_SQUARE_OFF_FAILED,
        QUANTITY_NOT_IN_LOTS, REDIRECT_URL_NOT_CONFIGURED, SANDBOX_TOKEN_NOT_ALLOWED,
        SCRIP_NOT_FOUND, SERVICE_UNAVAILABLE, SESSION_EXPIRED, SESSION_NOT_GENERATED,
        STATIC_IP_MISMATCH, STATIC_IP_MISSING, TOTP_NOT_ENABLED, UNHANDLED_ERROR,
        VALIDATION_ERROR, WRONG_TOKEN_AUDIENCE);
}
