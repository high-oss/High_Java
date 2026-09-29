// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.Set;

/**
 * One quote (market-watch) delta, delivered as the merged, complete {@link
 * #quote()} snapshot plus the public field names {@link #changedFields()}
 * that this particular tick actually moved — ticks are deltas (datafeed plan
 * Phase 0), so most fields on {@link #quote()} did not change on this event.
 */
public record QuoteEvent(Quote quote, Set<String> changedFields) implements FeedEvent {
}
