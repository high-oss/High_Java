// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.util.Locale;
import java.util.stream.Collectors;

/** Which HIGH environment to talk to. Default: {@link #PRODUCTION}. */
public enum Environment {
    PRODUCTION,
    SANDBOX;

    /** The wire/option-value spelling, e.g. {@code "sandbox"}. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses an option or environment-variable value, failing with a message
     * naming the valid values — at construction time, never at request time.
     */
    public static Environment fromString(String value, String source) {
        for (Environment env : values()) {
            if (env.wireValue().equals(value)) return env;
        }
        String valid = java.util.Arrays.stream(values())
            .map(Environment::wireValue)
            .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
            "Unknown HIGH environment \"" + value + "\" (" + source + "). Valid values: " + valid + ".");
    }
}
