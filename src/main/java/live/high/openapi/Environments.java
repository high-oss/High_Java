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
 * <p>{@code ws} is the datafeed socket host {@link live.high.openapi.HighFeed}
 * connects to (contract §9), declared here so the feed reuses this
 * configuration and these credentials rather than introducing a second config
 * surface. <b>Production only</b> — the real host is {@code
 * openapi-feed.high.live}; {@link Environment#SANDBOX}'s {@code ws} entry
 * below is never dialled, because {@code HighFeed} refuses a client
 * configured for sandbox at construction (there is no sandbox feed). It is
 * kept as an inert placeholder rather than {@code null} so {@link
 * ResolvedConfig#resolve} — used by the ordinary REST {@code HighClient} too,
 * where sandbox is a normal, supported environment — never has to null-check
 * it.
 */
public final class Environments {

    private Environments() {}

    /** One environment's API and datafeed-socket hosts. */
    public record Hosts(String api, String ws) {}

    private static final Map<Environment, Hosts> ENVIRONMENTS = buildMap();

    private static Map<Environment, Hosts> buildMap() {
        Map<Environment, Hosts> map = new EnumMap<>(Environment.class);
        map.put(Environment.PRODUCTION, new Hosts("https://openapi.high.live", "wss://openapi-feed.high.live"));
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
