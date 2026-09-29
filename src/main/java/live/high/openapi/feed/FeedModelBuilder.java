// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the public, typed models from a per-instrument merged raw-value map
 * (wire key → last-known raw text). The merge itself — folding a delta tick
 * over the previous state — lives in {@code HighFeed}; this class only knows
 * how to turn a complete merged map into {@link Quote}/{@link Depth}/{@link
 * IndexQuote}.
 */
final class FeedModelBuilder {

    private FeedModelBuilder() {}

    static Quote buildQuote(String scripKey, Map<String, String> merged) {
        return new Quote(
            scripKey,
            merged.get("ts"),
            FeedParsing.decimal(merged.get("ltp")),
            FeedParsing.integer(merged.get("ltq")),
            FeedParsing.dateTime(merged.get("ltt"), FeedParsing.LTT_FORMAT),
            FeedParsing.integer(merged.get("v")),
            FeedParsing.decimal(merged.get("to")),
            FeedParsing.decimal(merged.get("ap")),
            FeedParsing.decimal(merged.get("op")),
            FeedParsing.decimal(merged.get("h")),
            FeedParsing.decimal(merged.get("lo")),
            FeedParsing.decimal(merged.get("c")),
            FeedParsing.decimal(merged.get("yh")),
            FeedParsing.decimal(merged.get("yl")),
            FeedParsing.decimal(merged.get("cng")),
            FeedParsing.decimal(merged.get("nc")),
            FeedParsing.integer(merged.get("tbq")),
            FeedParsing.integer(merged.get("tsq")),
            FeedParsing.integer(merged.get("oi")),
            FeedParsing.decimal(merged.get("lcl")),
            FeedParsing.decimal(merged.get("ucl")),
            FeedParsing.dateTime(merged.get("fdtm"), FeedParsing.FDTM_FORMAT),
            FeedParsing.decimal(merged.get("mul")),
            FeedParsing.smallInteger(merged.get("prec")),
            unknownFields(merged, FeedFields.QUOTE_WIRE_KEYS, FeedFields.TOP_OF_BOOK_WIRE_KEYS));
    }

    static Depth buildTouchlineDepth(String scripKey, Map<String, String> merged) {
        DepthLevel bid = new DepthLevel(
            FeedParsing.decimal(merged.get("bp")), FeedParsing.integer(merged.get("bq")), null);
        DepthLevel ask = new DepthLevel(
            FeedParsing.decimal(merged.get("sp")), FeedParsing.integer(merged.get("bs")), null);
        return new Depth(scripKey, DepthSource.TOUCHLINE, List.of(bid), List.of(ask), Map.of());
    }

    static Depth buildFullDepth(String scripKey, Map<String, String> merged) {
        List<DepthLevel> bids = new ArrayList<>(5);
        List<DepthLevel> asks = new ArrayList<>(5);
        for (int level = 1; level <= 5; level++) {
            bids.add(new DepthLevel(
                FeedParsing.decimal(merged.get(FeedFields.depthBidPriceKey(level))),
                FeedParsing.integer(merged.get(FeedFields.depthBidQuantityKey(level))),
                FeedParsing.smallInteger(merged.get(FeedFields.depthBidOrdersKey(level)))));
            asks.add(new DepthLevel(
                FeedParsing.decimal(merged.get(FeedFields.depthAskPriceKey(level))),
                FeedParsing.integer(merged.get(FeedFields.depthAskQuantityKey(level))),
                FeedParsing.smallInteger(merged.get(FeedFields.depthAskOrdersKey(level)))));
        }
        return new Depth(scripKey, DepthSource.FULL_DEPTH, List.copyOf(bids), List.copyOf(asks),
            unknownFields(merged, FeedFields.DEPTH_WIRE_KEYS));
    }

    static IndexQuote buildIndex(String scripKey, Map<String, String> merged) {
        String indexName = merged.get("tk") != null ? merged.get("tk") : merged.get("ts");
        return new IndexQuote(
            scripKey,
            indexName,
            FeedParsing.decimal(merged.get("iv")),
            FeedParsing.decimal(merged.get("ic")),
            FeedParsing.decimal(merged.get("openingprice")),
            FeedParsing.decimal(merged.get("highprice")),
            FeedParsing.decimal(merged.get("lowprice")),
            FeedParsing.decimal(merged.get("cng")),
            FeedParsing.decimal(merged.get("nc")),
            FeedParsing.dateTime(merged.get("tvalue"), FeedParsing.FDTM_FORMAT),
            unknownFields(merged, FeedFields.INDEX_WIRE_KEYS));
    }

    @SafeVarargs
    private static Map<String, String> unknownFields(Map<String, String> merged, java.util.Set<String>... known) {
        Map<String, String> unknown = new LinkedHashMap<>();
        outer:
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            for (java.util.Set<String> set : known) {
                if (set.contains(entry.getKey())) continue outer;
            }
            unknown.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(unknown);
    }
}
