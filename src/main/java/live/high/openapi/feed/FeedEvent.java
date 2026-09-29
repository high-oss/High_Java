// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

/**
 * One tick delivery — {@link QuoteEvent}, {@link DepthEvent} or {@link
 * IndexEvent} — as published through {@code HighFeed.publisher()}. Plain
 * marker interface rather than a sealed hierarchy: all three live in this
 * package, and a caller who wants exhaustiveness pattern-matches on the
 * three known records directly.
 */
public interface FeedEvent {
}
