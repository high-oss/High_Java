// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.Set;

/** One index delta, delivered as the merged, complete {@link #index()} snapshot plus its {@link #changedFields()}. */
public record IndexEvent(IndexQuote index, Set<String> changedFields) implements FeedEvent {
}
