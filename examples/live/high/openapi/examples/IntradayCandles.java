// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import java.time.Instant;
import live.high.openapi.Environment;
import live.high.openapi.HighApiError;
import live.high.openapi.HighClient;
import live.high.openapi.HighClientOptions;
import live.high.openapi.generated.model.HistoricalCandles;
import live.high.openapi.generated.model.IntradayRequest;

/**
 * Fetches the most recent trading session's 5-minute candles for one
 * instrument, once by trading symbol and once by exchange segment + scrip
 * code. Both name the same instrument. Run it with:
 *
 * <pre>
 * HIGH_ENVIRONMENT=sandbox HIGH_ACCESS_TOKEN=... \
 *   gradle runExample -PmainClass=live.high.openapi.examples.IntradayCandles
 * </pre>
 */
public final class IntradayCandles {

    private IntradayCandles() {}

    public static void main(String[] args) {
        HighClient high = new HighClient(HighClientOptions.builder()
            .environment(Environment.SANDBOX)
            .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
            .build());

        try {
            // Form 1: named by trading symbol.
            HistoricalCandles bySymbol = high.scrips().intraday(new IntradayRequest()
                .tradingSymbol("RELIANCE-EQ")
                .interval(IntradayRequest.IntervalEnum._5M));
            printCandles("by tradingSymbol", bySymbol);

            // Form 2: named by exchange segment + scrip code (NSE 2885 is RELIANCE).
            HistoricalCandles byScripCode = high.scrips().intraday(new IntradayRequest()
                .exchangeSegment(IntradayRequest.ExchangeSegmentEnum.NSE)
                .scripCode("2885")
                .interval(IntradayRequest.IntervalEnum._5M));
            printCandles("by exchangeSegment + scripCode", byScripCode);
        } catch (HighApiError error) {
            System.err.println("HIGH API error " + error.status() + " " + error.code() + ": " + error.getMessage());
        }
    }

    /** Candles are columnar: index i across every list describes one candle. */
    private static void printCandles(String label, HistoricalCandles candles) {
        int total = candles.getClose().size();
        System.out.println(label + ": " + total + " " + candles.getInterval() + " candles for "
            + candles.getTradingSymbol());

        int shown = Math.min(5, total);
        for (int i = 0; i < shown; i++) {
            System.out.println("  " + Instant.ofEpochSecond(candles.getTimestamp().get(i))
                + " O=" + candles.getOpen().get(i)
                + " H=" + candles.getHigh().get(i)
                + " L=" + candles.getLow().get(i)
                + " C=" + candles.getClose().get(i)
                + " V=" + candles.getVolume().get(i));
        }
    }
}
