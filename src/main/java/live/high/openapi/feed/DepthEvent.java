// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.Set;

/**
 * One depth delta — either the top-of-book split out of a FULL-mode quote
 * tick, or a level from the dedicated depth channel; see {@link
 * Depth#source()}. {@link #changedFields()} names which levels/sides moved,
 * e.g. {@code "bids[0].price"}, {@code "asks[4].orders"} — 0-indexed to match
 * {@link Depth#bids()}/{@link Depth#asks()} directly.
 */
public record DepthEvent(Depth depth, Set<String> changedFields) implements FeedEvent {
}
