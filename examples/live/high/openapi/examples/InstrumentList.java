// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import java.util.stream.Stream;
import live.high.openapi.HighApiError;
import live.high.openapi.HighClient;
import live.high.openapi.generated.model.InstrumentFile;
import live.high.openapi.model.Instrument;

/**
 * Streams the equity instrument list and prints the first few rows. Needs no
 * credentials — the client below is built with none. Run it with:
 *
 * <pre>
 * gradle runExample -PmainClass=live.high.openapi.examples.InstrumentList
 * </pre>
 */
public final class InstrumentList {

    private InstrumentList() {}

    public static void main(String[] args) {
        HighClient high = new HighClient();

        try (Stream<Instrument> equities = high.instruments().stream(InstrumentFile.InstrumentEnum.EQUITY)) {
            equities.limit(10).forEach(i ->
                System.out.println(i.highTradingSymbol() + "  " + i.name() + "  lot=" + i.lotSize()));
        } catch (HighApiError error) {
            System.err.println("HIGH API error " + error.status() + " " + error.code() + ": " + error.getMessage());
        }
    }
}
