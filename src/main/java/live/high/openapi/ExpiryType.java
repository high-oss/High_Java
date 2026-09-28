// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

/**
 * Which derivative family to list expiries for — the {@code type} path
 * parameter of {@code GET /scrips/{symbol}/{type}/expiries}.
 *
 * <p>Hand-written, not generated: the generator only names a Java type for a
 * {@code $ref}-shared component schema, and this enum is declared inline on
 * the path parameter, not shared. A test pins these two values against
 * {@code generated/openapi.json}'s {@code
 * paths./scrips/{symbol}/{type}/expiries.get.parameters[type].schema.enum}
 * so a spec change breaks the build rather than silently compiling against a
 * stale set — the same discipline the generator gives every {@code $ref}'d
 * enum for free.
 */
public enum ExpiryType {
    FUTURES("futures"),
    OPTIONS("options");

    private final String wireValue;

    ExpiryType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
