// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpolates an operation's path template, percent-encoding every value.
 *
 * <p>Trading symbols legitimately contain {@code &} and spaces ({@code M&M-EQ},
 * {@code NIFTY 50}), which change the request if interpolated raw. Encoding is
 * not optional, and a {@code /} in a value is encoded too so a parameter can
 * never add a path segment of its own.
 */
public final class Paths {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private Paths() {}

    /**
     * @param template a path template such as {@code "/scrips/{symbol}/depth"}
     * @param params   values for each {@code {name}} placeholder in the template
     * @return the template with every placeholder replaced by its percent-encoded value
     * @throws IllegalArgumentException if a placeholder in the template has no matching value
     */
    public static String pathOf(String template, Map<String, ?> params) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = params.get(name);
            if (value == null) {
                throw new IllegalArgumentException(
                    "Missing path parameter \"" + name + "\" for " + template);
            }
            result.append(template, last, matcher.start());
            result.append(encode(String.valueOf(value)));
            last = matcher.end();
        }
        result.append(template, last, template.length());
        return result.toString();
    }

    private static String encode(String value) {
        // URLEncoder is the form-encoding flavour (space -> '+'); path segments need
        // '%20', and '*' and '~' must stay literal per RFC 3986's unreserved set.
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
    }
}
