// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import live.high.openapi.HighFeedError;
import org.junit.jupiter.api.Test;

class ScripKeyTranslatorTest {

    // --- non-index: prefix-derived translation ------------------------------

    @Test
    void derivesTheFeedSegmentFromEachKnownPrefix() {
        assertEquals("nse_cm|15563", ScripKeyTranslator.translateNonIndex("NSE@15563"));
        assertEquals("bse_cm|532540", ScripKeyTranslator.translateNonIndex("BSE@532540"));
        assertEquals("nse_fo|52190", ScripKeyTranslator.translateNonIndex("NSEFO@52190"));
        assertEquals("bse_fo|842150", ScripKeyTranslator.translateNonIndex("BSEFO@842150"));
        assertEquals("mcx_fo|227915", ScripKeyTranslator.translateNonIndex("MCXFO@227915"));
    }

    @Test
    void rejectsMcxSpotByName() {
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("MCX@100"));
        assertEquals(HighFeedError.Reason.UNSUPPORTED_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("MCX@100"));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("mcx spot"));
    }

    @Test
    void rejectsAnUnknownPrefixByName() {
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("NYSE@1"));
        assertEquals(HighFeedError.Reason.UNSUPPORTED_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("NYSE@1"));
    }

    @Test
    void rejectsAKeyWithNoAtSign() {
        assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("garbage"));
    }

    // --- index: table lookup only, first --------------------------------------

    @Test
    void translatesAnIndexKeyByNameNotToken() {
        assertEquals("nse_cm|Nifty 50", ScripKeyTranslator.translateIndex("NSE@26000"));
        assertEquals("nse_cm|Nifty Bank", ScripKeyTranslator.translateIndex("NSE@26009"));
        assertEquals("bse_cm|SENSEX", ScripKeyTranslator.translateIndex("BSE@19000"));
    }

    @Test
    void indexMethodRejectsANonIndexKeyByName() {
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateIndex("NSE@2885"));
        assertEquals(HighFeedError.Reason.UNSUPPORTED_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("NSE@2885"));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("not an index"));
    }

    // --- the coordinator's split: each direction rejects the other's keys ----

    @Test
    void nonIndexMethodRejectsAnIndexKeyByName() {
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("NSE@26000"));
        assertEquals(HighFeedError.Reason.UNSUPPORTED_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("NSE@26000"));
        assertTrue(error.getMessage().contains("subscribeIndices"));
    }

    // --- ambiguous: one token, two indices — the third rejection case --------

    @Test
    void translateIndexRejectsAnAmbiguousKeyNamingBothCandidates() {
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateIndex("NSE@26002"));
        assertEquals(HighFeedError.Reason.AMBIGUOUS_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("NSE@26002"));
        assertTrue(error.getMessage().contains("Nifty FMCG"));
        assertTrue(error.getMessage().contains("Nifty50 PR 2x Lev"));
    }

    @Test
    void translateNonIndexAlsoRejectsAnAmbiguousKeyRatherThanTreatingItAsAnOrdinaryToken() {
        // The failure this must prevent: NSE@26002 looks exactly like an
        // ordinary NSE@ cash-market key, so without this check it would
        // translate to "nse_cm|26002" — a token the feed does not carry —
        // and the subscription would be accepted and simply never tick.
        HighFeedError error = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("NSE@26002"));
        assertEquals(HighFeedError.Reason.AMBIGUOUS_SCRIP, error.reason());
        assertTrue(error.getMessage().contains("NSE@26002"));
    }

    @Test
    void everyAmbiguousKeyIsRejectedByBothMethods() {
        for (String scripKey : IndexFeedMap.AMBIGUOUS.keySet()) {
            HighFeedError fromIndex = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateIndex(scripKey));
            assertEquals(HighFeedError.Reason.AMBIGUOUS_SCRIP, fromIndex.reason());
            HighFeedError fromNonIndex = assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex(scripKey));
            assertEquals(HighFeedError.Reason.AMBIGUOUS_SCRIP, fromNonIndex.reason());
        }
    }

    @Test
    void wouldHaveMistranslatedAnIndexKeyThroughThePrefixRuleIfNotCaught() {
        // The defect the coordinator's split closes: NSE@26000 (Nifty 50) would
        // otherwise fall through the ordinary prefix rule to "nse_cm|26000" — a
        // token the feed does not know — and the subscription would be accepted
        // and simply never tick. Confirms translateIndex resolves it correctly
        // and translateNonIndex refuses it outright rather than producing that.
        assertEquals("nse_cm|Nifty 50", ScripKeyTranslator.translateIndex("NSE@26000"));
        assertThrows(HighFeedError.class, () -> ScripKeyTranslator.translateNonIndex("NSE@26000"));
    }
}
