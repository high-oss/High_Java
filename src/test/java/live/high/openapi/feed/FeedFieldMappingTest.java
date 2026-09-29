// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the two traps datafeed plan Phase 0/"Field names" calls out
 * explicitly: {@code cng}/{@code nc} are the opposite of the vendor's own
 * sample README, and the depth level numbering has two different bases.
 */
class FeedFieldMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void cngIsTheAbsoluteChangeAndNcIsThePercentage() {
        // The plan's own worked example: ltp 1905.65, c (previousClose) 1859.05,
        // cng 46.60 (the absolute difference), nc 2.51 (46.60 / 1859.05 as a
        // percentage). The vendor's sample README labels these backwards.
        ObjectNode tick = MAPPER.createObjectNode();
        tick.put("e", "nse_cm");
        tick.put("tk", "15563");
        tick.put("name", "sf");
        tick.put("ltp", "1905.65");
        tick.put("c", "1859.05");
        tick.put("cng", "46.60");
        tick.put("nc", "2.51");

        TickProcessor processor = new TickProcessor();
        List<FeedEvent> events = processor.processQuoteTick("NSE@15563", tick);
        assertEquals(1, events.size());
        QuoteEvent event = (QuoteEvent) events.get(0);

        assertEquals(new BigDecimal("46.60"), event.quote().change());
        assertEquals(new BigDecimal("2.51"), event.quote().changePercent());
        assertTrue(event.changedFields().contains("change"));
        assertTrue(event.changedFields().contains("changePercent"));
    }

    @Test
    void depthLevelOnePairsBpWithBno1AndLevelFivePairsBp4WithBno5() {
        ObjectNode tick = MAPPER.createObjectNode();
        tick.put("e", "nse_cm");
        tick.put("tk", "15563");
        tick.put("name", "dp");
        tick.put("bp", "100.00");
        tick.put("bq", "10");
        tick.put("bno1", "1");
        tick.put("bp1", "99.50");
        tick.put("bq1", "20");
        tick.put("bno2", "2");
        tick.put("bp2", "99.00");
        tick.put("bq2", "30");
        tick.put("bno3", "3");
        tick.put("bp3", "98.50");
        tick.put("bq3", "40");
        tick.put("bno4", "4");
        tick.put("bp4", "98.00");
        tick.put("bq4", "50");
        tick.put("bno5", "5");
        tick.put("sp", "101.00");
        tick.put("bs", "11");
        tick.put("sno1", "1");

        TickProcessor processor = new TickProcessor();
        List<FeedEvent> events = processor.processDepthTick("NSE@15563", tick);
        assertEquals(1, events.size());
        Depth depth = ((DepthEvent) events.get(0)).depth();

        assertEquals(DepthSource.FULL_DEPTH, depth.source());
        assertEquals(5, depth.bids().size());
        assertEquals(5, depth.asks().size());

        // Level 1: unnumbered price/quantity (bp/bq), order count "bno1".
        assertEquals(new BigDecimal("100.00"), depth.bids().get(0).price());
        assertEquals(Long.valueOf(10), depth.bids().get(0).quantity());
        assertEquals(Integer.valueOf(1), depth.bids().get(0).orders());

        // Level 5: price/quantity "bp4"/"bq4" (suffix 4, not 5), order count "bno5".
        assertEquals(new BigDecimal("98.00"), depth.bids().get(4).price());
        assertEquals(Long.valueOf(50), depth.bids().get(4).quantity());
        assertEquals(Integer.valueOf(5), depth.bids().get(4).orders());

        // Ask side uses "bs" (not "sq") for quantity per the plan's field map.
        assertEquals(new BigDecimal("101.00"), depth.asks().get(0).price());
        assertEquals(Long.valueOf(11), depth.asks().get(0).quantity());
        assertEquals(Integer.valueOf(1), depth.asks().get(0).orders());
    }

    @Test
    void touchlineDepthIsExactlyOneLevelAndNeverPresentedAsFive() {
        ObjectNode tick = MAPPER.createObjectNode();
        tick.put("e", "nse_cm");
        tick.put("tk", "15563");
        tick.put("name", "sf");
        tick.put("bp", "100.00");
        tick.put("bq", "10");
        tick.put("sp", "101.00");
        tick.put("bs", "11");

        TickProcessor processor = new TickProcessor();
        List<FeedEvent> events = processor.processQuoteTick("NSE@15563", tick);
        assertEquals(1, events.size(), "no quote fields present — only a touchline depth event should fire");
        Depth depth = ((DepthEvent) events.get(0)).depth();

        assertEquals(DepthSource.TOUCHLINE, depth.source());
        assertEquals(1, depth.bids().size());
        assertEquals(1, depth.asks().size());
        assertNull(depth.bids().get(0).orders(), "order counts are not carried at touchline");
    }

    @Test
    void aFullMarketWatchTickSplitsIntoAQuoteEventAndATouchlineDepthEvent() {
        ObjectNode tick = MAPPER.createObjectNode();
        tick.put("e", "nse_cm");
        tick.put("tk", "15563");
        tick.put("name", "sf");
        tick.put("ltp", "1905.65");
        tick.put("bp", "1905.50");
        tick.put("bq", "10");
        tick.put("sp", "1905.75");
        tick.put("bs", "12");

        TickProcessor processor = new TickProcessor();
        List<FeedEvent> events = processor.processQuoteTick("NSE@15563", tick);

        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof QuoteEvent);
        assertTrue(events.get(1) instanceof DepthEvent);
        assertEquals(DepthSource.TOUCHLINE, ((DepthEvent) events.get(1)).depth().source());
    }

    @Test
    void mergesSeveralDeltaTicksIntoOneCompleteSnapshot() {
        TickProcessor processor = new TickProcessor();

        ObjectNode tick1 = MAPPER.createObjectNode();
        tick1.put("e", "nse_cm");
        tick1.put("tk", "15563");
        tick1.put("name", "sf");
        tick1.put("op", "1900.00");
        tick1.put("h", "1910.00");
        processor.processQuoteTick("NSE@15563", tick1);

        ObjectNode tick2 = MAPPER.createObjectNode();
        tick2.put("e", "nse_cm");
        tick2.put("tk", "15563");
        tick2.put("name", "sf");
        tick2.put("ltp", "1905.65");
        List<FeedEvent> afterTick2 = processor.processQuoteTick("NSE@15563", tick2);

        ObjectNode tick3 = MAPPER.createObjectNode();
        tick3.put("e", "nse_cm");
        tick3.put("tk", "15563");
        tick3.put("name", "sf");
        tick3.put("lo", "1898.00");
        List<FeedEvent> afterTick3 = processor.processQuoteTick("NSE@15563", tick3);

        // Each event's changedFields names only what THAT tick moved...
        assertEquals(java.util.Set.of("lastTradedPrice"), ((QuoteEvent) afterTick2.get(0)).changedFields());
        assertEquals(java.util.Set.of("low"), ((QuoteEvent) afterTick3.get(0)).changedFields());

        // ...but the snapshot handed out each time is complete, carrying every
        // field merged so far — a caller never sees a null for open/high just
        // because the tick that set them was two deltas ago.
        Quote latest = ((QuoteEvent) afterTick3.get(0)).quote();
        assertEquals(new BigDecimal("1900.00"), latest.open());
        assertEquals(new BigDecimal("1910.00"), latest.high());
        assertEquals(new BigDecimal("1905.65"), latest.lastTradedPrice());
        assertEquals(new BigDecimal("1898.00"), latest.low());
    }

    @Test
    void aTickThatOnlyMovesLtpRaisesNoDepthEvent() {
        TickProcessor processor = new TickProcessor();
        ObjectNode first = MAPPER.createObjectNode();
        first.put("e", "nse_cm");
        first.put("tk", "15563");
        first.put("name", "sf");
        first.put("ltp", "100.00");
        first.put("bp", "99.90");
        processor.processQuoteTick("NSE@15563", first);

        ObjectNode second = MAPPER.createObjectNode();
        second.put("e", "nse_cm");
        second.put("tk", "15563");
        second.put("name", "sf");
        second.put("ltp", "100.05");

        List<FeedEvent> events = processor.processQuoteTick("NSE@15563", second);
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof QuoteEvent);
        assertEquals(new BigDecimal("100.05"), ((QuoteEvent) events.get(0)).quote().lastTradedPrice());
    }
}
