// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Parses the feed's wire values. Every value arrives as a string (Phase 0:
 * "Prices are already-scaled decimal strings... must be parsed to the
 * language's decimal type, never to a binary float"), so every parser here
 * trims first (the index sample's {@code " 9408.60"} leading space) and
 * returns {@code null} for a blank or missing value rather than guessing.
 *
 * <p>{@code ltt} and {@code fdtm}/{@code tvalue} use two different formats
 * and neither carries a timezone — they are IST wall-clock, parsed as {@link
 * LocalDateTime} rather than mis-parsed as UTC.
 */
final class FeedParsing {

    private FeedParsing() {}

    static final DateTimeFormatter LTT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH);

    /** Also used for the index frame's {@code tvalue} — same pattern, different field. */
    static final DateTimeFormatter FDTM_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);

    static String trimmed(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }

    static BigDecimal decimal(String raw) {
        String v = trimmed(raw);
        return v == null ? null : new BigDecimal(v);
    }

    static Long integer(String raw) {
        String v = trimmed(raw);
        if (v == null) return null;
        // Quantities arrive as plain integers, but a tolerant parse (via
        // BigDecimal) survives a stray ".0" without misreporting the value.
        return v.indexOf('.') < 0 ? Long.valueOf(v) : new BigDecimal(v).longValueExact();
    }

    static Integer smallInteger(String raw) {
        Long v = integer(raw);
        return v == null ? null : Math.toIntExact(v);
    }

    static LocalDateTime dateTime(String raw, DateTimeFormatter format) {
        String v = trimmed(raw);
        return v == null ? null : LocalDateTime.parse(v, format);
    }
}
