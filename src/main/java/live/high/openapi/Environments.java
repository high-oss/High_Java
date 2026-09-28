// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import java.util.EnumMap;
import java.util.Map;

/**
 * The hosts for each environment. {@code api} comes from the spec's {@code
 * servers} block, by its {@code x-environment} name — a test asserts these
 * still match the pinned spec.
 *
 * <p>{@code ws} is the datafeed socket host, reserved for the feed client
 * that lands in its own plan (contract §9). It is declared here so the feed
 * reuses this configuration and these credentials rather than introducing a
 * second config surface. Until the socket contract exists, treat it as
 * provisional and override it with {@code wsBaseUrl}.
 */
public final class Environments {

    private Environments() {}

    /** One environment's API and datafeed-socket hosts. */
    public record Hosts(String api, String ws) {}

    private static final Map<Environment, Hosts> ENVIRONMENTS = buildMap();

    private static Map<Environment, Hosts> buildMap() {
        Map<Environment, Hosts> map = new EnumMap<>(Environment.class);
        map.put(Environment.PRODUCTION, new Hosts("https://openapi.high.live", "wss://openapi.high.live"));
        map.put(Environment.SANDBOX, new Hosts("https://sandbox.high.live", "wss://sandbox.high.live"));
        return Map.copyOf(map);
    }

    public static Hosts get(Environment environment) {
        return ENVIRONMENTS.get(environment);
    }

    /** All environments, for tests that walk the whole set. */
    public static Map<Environment, Hosts> all() {
        return ENVIRONMENTS;
    }
}
