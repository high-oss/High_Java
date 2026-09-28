// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Log levels, least to most verbose. Setting a level prints that level and
 * everything more severe: {@link #WARN} prints warnings and errors,
 * {@link #DEBUG} prints everything. Default is {@link #SILENT} — a library
 * that prints uninvited is a bad citizen in someone else's application.
 */
public enum LogLevel {
    SILENT(0),
    ERROR(1),
    WARN(2),
    INFO(3),
    DEBUG(4);

    final int rank;

    LogLevel(int rank) {
        this.rank = rank;
    }

    /** The wire/option-value spelling, e.g. {@code "silent"}. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses an option or environment-variable value, failing with a message
     * naming the valid values — at construction time, never at request time.
     */
    public static LogLevel fromString(String value, String source) {
        for (LogLevel level : values()) {
            if (level.wireValue().equals(value)) return level;
        }
        String valid = java.util.Arrays.stream(values())
            .map(LogLevel::wireValue)
            .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
            "Unknown HIGH log level \"" + value + "\" (" + source + "). Valid values: " + valid + ".");
    }
}
