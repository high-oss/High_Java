// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The closed set of non-index HIGH scrip-key prefixes the datafeed covers,
 * each mapping to the feed's own segment code — datafeed plan Phase 1's
 * "derived from the key" table.
 *
 * <p>Deliberately a closed enum, not a fallback: a prefix outside this set —
 * including {@code MCX} (spot), which is known and named but has no feed
 * code — is rejected by {@link ScripKeyTranslator} rather than guessed at, so
 * adding a segment later is one edit here instead of a silent behaviour
 * change.
 */
enum ScripKeyPrefix {
    NSE("NSE", "nse_cm"),
    BSE("BSE", "bse_cm"),
    NSEFO("NSEFO", "nse_fo"),
    BSEFO("BSEFO", "bse_fo"),
    MCXFO("MCXFO", "mcx_fo");

    /** Known, named, but explicitly outside {@link #values()} — see {@link ScripKeyTranslator}. */
    static final String MCX_SPOT_PREFIX = "MCX";

    private final String prefix;
    private final String feedSegment;

    ScripKeyPrefix(String prefix, String feedSegment) {
        this.prefix = prefix;
        this.feedSegment = feedSegment;
    }

    String prefix() {
        return prefix;
    }

    String feedSegment() {
        return feedSegment;
    }

    static ScripKeyPrefix byPrefix(String prefix) {
        for (ScripKeyPrefix p : values()) {
            if (p.prefix.equals(prefix)) return p;
        }
        return null;
    }

    static String supportedPrefixes() {
        return Arrays.stream(values()).map(ScripKeyPrefix::prefix).collect(Collectors.joining(", "));
    }
}
