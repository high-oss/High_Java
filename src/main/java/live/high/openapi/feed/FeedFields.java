// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The field-name map from datafeed plan Phase "Field names" and shared
 * contract §9, verbatim. Wire keys never reach the public surface — every
 * model field here is the public name the plan pins, including the pair the
 * vendor's own sample README gets backwards: {@code cng} is the absolute
 * {@code change}, {@code nc} is the {@code changePercent} (the plan's own
 * worked example — {@code ltp} 1905.65, {@code c} 1859.05, {@code cng}
 * 46.60, {@code nc} 2.51 — is pinned by {@code FeedFieldMappingTest}).
 */
final class FeedFields {

    private FeedFields() {}

    /** {@code e}, {@code tk}, {@code name} are routing/identity fields — internal, never modelled as unknown fields. */
    static final Set<String> ROUTING_KEYS = Set.of("e", "tk", "name");

    // --- Quote (market watch, frame "sf") -----------------------------------

    static final Map<String, String> QUOTE_WIRE_TO_PUBLIC = mapOf(
        "ts", "tradingSymbol",
        "ltp", "lastTradedPrice",
        "ltq", "lastTradedQuantity",
        "ltt", "lastTradedTime",
        "v", "volume",
        "to", "turnover",
        "ap", "averageTradePrice",
        "op", "open",
        "h", "high",
        "lo", "low",
        "c", "previousClose",
        "yh", "yearHigh",
        "yl", "yearLow",
        "cng", "change",
        "nc", "changePercent",
        "tbq", "totalBuyQuantity",
        "tsq", "totalSellQuantity",
        "oi", "openInterest",
        "lcl", "lowerCircuitLimit",
        "ucl", "upperCircuitLimit",
        "fdtm", "feedTime",
        "mul", "multiplier",
        "prec", "precision");

    static final Set<String> QUOTE_WIRE_KEYS = QUOTE_WIRE_TO_PUBLIC.keySet();

    // --- Top-of-book, split out of a FULL-mode quote tick -------------------

    static final Map<String, String> TOP_OF_BOOK_WIRE_TO_PUBLIC = mapOf(
        "bp", "bestBidPrice",
        "bq", "bestBidQuantity",
        "sp", "bestAskPrice",
        "bs", "bestAskQuantity");

    static final Set<String> TOP_OF_BOOK_WIRE_KEYS = TOP_OF_BOOK_WIRE_TO_PUBLIC.keySet();

    // --- Depth (frame "dp") --------------------------------------------------
    // Price/quantity arrays start unnumbered (level 1); order-count arrays
    // start at 1. Level n (1-based) is bp{n-1}/bq{n-1} (n==1 => bp/bq) but
    // bno{n}/sno{n} — the pairing DepthLevelPairingTest pins.

    static final Set<String> DEPTH_WIRE_KEYS = Set.of(
        "bp", "bp1", "bp2", "bp3", "bp4",
        "bq", "bq1", "bq2", "bq3", "bq4",
        "bno1", "bno2", "bno3", "bno4", "bno5",
        "sp", "sp1", "sp2", "sp3", "sp4",
        "bs", "bs1", "bs2", "bs3", "bs4",
        "sno1", "sno2", "sno3", "sno4", "sno5");

    static String depthBidPriceKey(int level) {
        return level == 1 ? "bp" : "bp" + (level - 1);
    }

    static String depthBidQuantityKey(int level) {
        return level == 1 ? "bq" : "bq" + (level - 1);
    }

    static String depthBidOrdersKey(int level) {
        return "bno" + level;
    }

    static String depthAskPriceKey(int level) {
        return level == 1 ? "sp" : "sp" + (level - 1);
    }

    static String depthAskQuantityKey(int level) {
        return level == 1 ? "bs" : "bs" + (level - 1);
    }

    static String depthAskOrdersKey(int level) {
        return "sno" + level;
    }

    // --- Index (frame "if") ---------------------------------------------------

    static final Map<String, String> INDEX_WIRE_TO_PUBLIC = mapOf(
        "tk", "indexName",
        "ts", "indexName",
        "iv", "indexValue",
        "ic", "previousClose",
        "openingprice", "open",
        "highprice", "high",
        "lowprice", "low",
        "cng", "change",
        "nc", "changePercent",
        "tvalue", "feedTime");

    static final Set<String> INDEX_WIRE_KEYS = INDEX_WIRE_TO_PUBLIC.keySet();

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
