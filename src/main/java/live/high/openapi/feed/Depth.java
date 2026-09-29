// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.List;
import java.util.Map;

/**
 * An order-book snapshot, merged from every delta tick received so far. See
 * {@link #source()} before assuming a level count — a {@link
 * DepthSource#TOUCHLINE} book always has exactly one level per side, a {@link
 * DepthSource#FULL_DEPTH} one always has exactly five.
 *
 * @param unknownFields any field this SDK's field map does not name, kept
 *                       verbatim (raw wire text); always empty for {@link
 *                       DepthSource#TOUCHLINE} — its four fields are all
 *                       named
 */
public record Depth(
    String scripKey,
    DepthSource source,
    List<DepthLevel> bids,
    List<DepthLevel> asks,
    Map<String, String> unknownFields
) {
}
