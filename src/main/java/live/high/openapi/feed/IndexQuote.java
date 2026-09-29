// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * An index snapshot, merged from every delta tick received so far and keyed
 * by the caller's own {@link #scripKey()}. {@link #feedTime()} is IST
 * wall-clock with no offset.
 *
 * @param unknownFields any field this SDK's field map does not name, kept
 *                       verbatim (raw wire text)
 */
public record IndexQuote(
    String scripKey,
    String indexName,
    BigDecimal indexValue,
    BigDecimal previousClose,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal change,
    BigDecimal changePercent,
    LocalDateTime feedTime,
    Map<String, String> unknownFields
) {
}
