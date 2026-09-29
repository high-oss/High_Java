// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.math.BigDecimal;

/**
 * One bid or ask level. {@code orders} is {@code null} for a {@link
 * DepthSource#TOUCHLINE} level — order counts are only carried on the
 * dedicated depth channel.
 */
public record DepthLevel(BigDecimal price, Long quantity, Integer orders) {
}
