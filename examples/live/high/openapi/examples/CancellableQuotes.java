// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import live.high.openapi.Environment;
import live.high.openapi.HighClient;
import live.high.openapi.HighClientOptions;
import live.high.openapi.generated.model.QuotesRequest;
import live.high.openapi.generated.model.Touchline;

/**
 * Fetches a quote asynchronously and cancels it if it is not back within a
 * second — demonstrating the SDK's cancellation story (see the README's
 * "Cancellation" section). Run it with:
 *
 * <pre>
 * HIGH_ENVIRONMENT=sandbox HIGH_ACCESS_TOKEN=... \
 *   gradle runExample -PmainClass=live.high.openapi.examples.CancellableQuotes
 * </pre>
 */
public final class CancellableQuotes {

    private CancellableQuotes() {}

    public static void main(String[] args) throws Exception {
        HighClient high = new HighClient(HighClientOptions.builder()
            .environment(Environment.SANDBOX)
            .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
            .build());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CompletableFuture<Map<String, Touchline>> quotes =
                high.scrips().quotesAsync(new QuotesRequest().symbols(List.of("RELIANCE-EQ")));

            scheduler.schedule(() -> quotes.cancel(true), 1, TimeUnit.SECONDS);

            try {
                Map<String, Touchline> result = quotes.join();
                System.out.println("RELIANCE-EQ LTP: " + result.get("RELIANCE-EQ").getLTP());
            } catch (CancellationException e) {
                System.out.println("Cancelled — took longer than 1s.");
            }
        } finally {
            scheduler.shutdown();
        }
    }
}
