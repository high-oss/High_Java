// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import java.util.List;
import live.high.openapi.HighClientOptions;
import live.high.openapi.HighFeed;
import live.high.openapi.HighFeedError;
import live.high.openapi.LogLevel;
import live.high.openapi.feed.DepthEvent;
import live.high.openapi.feed.FeedListener;
import live.high.openapi.feed.IndexEvent;
import live.high.openapi.feed.QuoteEvent;

/**
 * Streams touchline quotes, depth and one index for a few instruments and
 * prints each event. Run it with:
 *
 * <pre>
 * HIGH_ACCESS_TOKEN=... gradle runExample -PmainClass=live.high.openapi.examples.LiveQuotes
 * </pre>
 *
 * <p>Not run as part of this SDK's own test suite — {@code gradle test}
 * never opens a socket to the real datafeed host. It is compiled (so it
 * cannot silently drift from the public surface) and left for a developer to
 * run by hand against a real access token.
 */
public final class LiveQuotes {

    private LiveQuotes() {}

    public static void main(String[] args) throws InterruptedException {
        HighFeed feed = new HighFeed(HighClientOptions.builder()
            // No `.environment(...)` here: PRODUCTION is the default, and it is
            // also the *only* environment the datafeed has — HighFeed refuses a
            // client built with Environment.SANDBOX at construction.
            .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
            .logLevel(LogLevel.INFO)
            .build());

        feed.addListener(new FeedListener() {
            @Override
            public void onConnected() {
                System.out.println("connected (maxScripPerConn=" + feed.maxScripPerConn()
                    + ", maxScripPerReq=" + feed.maxScripPerReq() + ")");
            }

            @Override
            public void onDisconnected(String reason) {
                System.out.println("disconnected: " + reason);
            }

            @Override
            public void onQuote(QuoteEvent event) {
                System.out.println(event.quote().scripKey() + " ltp=" + event.quote().lastTradedPrice()
                    + " changed=" + event.changedFields());
            }

            @Override
            public void onDepth(DepthEvent event) {
                System.out.println(event.depth().scripKey() + " " + event.depth().source()
                    + " bestBid=" + event.depth().bids().get(0));
            }

            @Override
            public void onIndex(IndexEvent event) {
                System.out.println(event.index().indexName() + " = " + event.index().indexValue());
            }

            @Override
            public void onError(HighFeedError error) {
                System.err.println("feed error (" + error.reason() + "): " + error.getMessage());
            }
        });

        try {
            feed.connect();
        } catch (HighFeedError error) {
            System.err.println("could not connect: " + error.reason() + " " + error.getMessage());
            return;
        }

        feed.subscribeQuotes(List.of("NSE@2885", "BSE@532540"));
        feed.subscribeDepth(List.of("NSE@2885"));
        feed.subscribeIndices(List.of("NSE@26000")); // Nifty 50

        Thread.sleep(30_000);
        feed.close();
    }
}
