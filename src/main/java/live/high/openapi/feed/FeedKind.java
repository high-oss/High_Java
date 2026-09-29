// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

/**
 * Internal bookkeeping only — the wire's three channel families ({@code mw}
 * market watch/quote, {@code dp} depth, {@code if} indices) and their {@code
 * s}/{@code u}/{@code sp} (subscribe/unsubscribe/snapshot) suffixes.
 *
 * <p>Not part of the public surface: {@code HighFeed} exposes dedicated
 * {@code subscribeQuotes}/{@code subscribeDepth}/{@code subscribeIndices}
 * methods (and their unsubscribe/snapshot counterparts) instead of a shared
 * method taking one of these as a parameter, so a caller cannot subscribe to
 * an index through the quote method by passing the "wrong" enum value — the
 * method chosen at the call site already says which channel is meant, and
 * {@code ScripKeyTranslator} enforces that the key given matches it.
 */
enum FeedKind {
    QUOTE("mw"),
    DEPTH("dp"),
    INDEX("if");

    private final String wirePrefix;

    FeedKind(String wirePrefix) {
        this.wirePrefix = wirePrefix;
    }

    String subscribeType() {
        return wirePrefix + "s";
    }

    String unsubscribeType() {
        return wirePrefix + "u";
    }

    String snapshotType() {
        return wirePrefix + "sp";
    }
}
