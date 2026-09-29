// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import live.high.openapi.HighFeedError;

/**
 * Receives {@code HighFeed} events. Every method has a no-op default, so a
 * listener implements only what it needs. Called from the socket's own
 * reader thread — do not block here; a slow listener delays every
 * subsequent frame.
 */
public interface FeedListener {

    /** Fired once the connection is authenticated and — after a reconnect — every prior subscription has been resent. */
    default void onConnected() {}

    /** Fired when the connection drops, whether by {@code close()} or a transport failure about to trigger a reconnect. */
    default void onDisconnected(String reason) {}

    default void onQuote(QuoteEvent event) {}

    default void onDepth(DepthEvent event) {}

    default void onIndex(IndexEvent event) {}

    /** A terminal failure — auth rejected, a scrip key this connection refuses, or a reconnect attempt's own auth rejection (which stops the reconnect loop; see {@link HighFeedError.Reason}). */
    default void onError(HighFeedError error) {}
}
