// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

/**
 * Which frame a {@link Depth} snapshot was built from — so a caller can never
 * mistake a one-level book for a five-level one with four empty rows, which
 * reads as a suddenly-illiquid instrument (datafeed plan Phase 3).
 */
public enum DepthSource {
    /** Top-of-book only, split out of a FULL-mode quote tick. Exactly one level in {@link Depth#bids()}/{@link Depth#asks()}, and its {@link DepthLevel#orders()} is always {@code null} — order counts are not carried at touchline. */
    TOUCHLINE,
    /** The dedicated depth channel. Exactly five levels in {@link Depth#bids()}/{@link Depth#asks()}. */
    FULL_DEPTH
}
