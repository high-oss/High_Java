// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A market-watch (touchline) snapshot for one instrument, merged from every
 * delta tick received so far and keyed by the caller's own {@link
 * #scripKey()} — never the feed's {@code e}/{@code tk}.
 *
 * <p>{@link #lastTradedTime()} and {@link #feedTime()} are IST wall-clock
 * with no offset (two different wire formats — see {@code FeedParsing}); do
 * not treat either as UTC. {@link #change()} is the absolute move, {@link
 * #changePercent()} the percentage — the plan's own worked example pins this
 * because the vendor's sample README has the two backwards.
 *
 * @param unknownFields any field this SDK's field map does not name, kept
 *                       verbatim (raw wire text) so a vendor addition reaches
 *                       the caller instead of vanishing
 */
public record Quote(
    String scripKey,
    String tradingSymbol,
    BigDecimal lastTradedPrice,
    Long lastTradedQuantity,
    LocalDateTime lastTradedTime,
    Long volume,
    BigDecimal turnover,
    BigDecimal averageTradePrice,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal previousClose,
    BigDecimal yearHigh,
    BigDecimal yearLow,
    BigDecimal change,
    BigDecimal changePercent,
    Long totalBuyQuantity,
    Long totalSellQuantity,
    Long openInterest,
    BigDecimal lowerCircuitLimit,
    BigDecimal upperCircuitLimit,
    LocalDateTime feedTime,
    BigDecimal multiplier,
    Integer precision,
    Map<String, String> unknownFields
) {
}
