// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.List;
import java.util.Map;

/**
 * GENERATED — do not hand-edit. Rendered from the canonical
 * {@code C:\Users\vipul\Projects\High\sdk\index-feed-map.json} (a checked-in
 * copy ships at {@code src/test/resources/index-feed-map.json} so
 * {@code IndexFeedMapGenerationTest} can rebuild this table in Docker, where
 * the canonical file outside this repo is not mounted).
 *
 * <p>An index shares the {@code NSE@}/{@code BSE@} prefix with cash scrips —
 * indices are subscribed by name, not token — so {@link
 * live.high.openapi.feed.ScripKeyTranslator} looks a key up here <b>first</b>;
 * a miss falls through to the prefix rule. See datafeed plan Phase 1.
 *
 * <p>The canonical file is an object, not a flat list: an {@code indices}
 * array — {@link #BY_SCRIP_KEY} below, one entry per row — and a separate
 * {@code ambiguous} array for scrip keys the source itself flags as
 * unresolvable: the scrip master genuinely shares one token between two
 * index names (e.g. {@code NSE@26002} is both "Nifty FMCG" and "Nifty50 PR
 * 2x Lev"). Those six keys are {@link #AMBIGUOUS} below, never {@link
 * #BY_SCRIP_KEY} — {@code ScripKeyTranslator} rejects them by name, listing
 * the candidates, rather than picking one and silently streaming the wrong
 * index under a name the caller trusts. Four further index rows (HANGSENG
 * BEES-NAV, S&amp;P BSE CARBONEX, S&amp;P BSE GREENEX, S&amp;P BSE DOLLEX 30)
 * have no feed counterpart at all and are simply absent from both arrays —
 * the datafeed plan records why for each.
 *
 * <p>{@code IndexFeedMapGenerationTest} re-derives both {@link
 * #BY_SCRIP_KEY} and {@link #AMBIGUOUS} from the checked-in JSON, so either
 * table drifting from the canonical source is a failing test, not a silent
 * gap.
 */
public final class IndexFeedMap {

    private IndexFeedMap() {}

    /** One row: the HIGH scrip key, the feed's segment, the feed's own symbol (subscribe by this), and its display name. */
    public record Entry(String scripKey, String feedSegment, String feedSymbol, String name) {}

    /**
     * Scrip keys the scrip master maps to more than one index, each with the
     * candidate index names it could mean. Deliberately excluded from {@link
     * #BY_SCRIP_KEY} — see the class Javadoc — and checked by {@code
     * ScripKeyTranslator} before either lookup so both the index methods and
     * the quote/depth methods reject one of these with a message naming the
     * candidates, rather than the quote/depth path silently mistranslating it
     * through the ordinary prefix rule.
     */
    public static final Map<String, List<String>> AMBIGUOUS = Map.ofEntries(
        Map.entry("NSE@26002", List.of("Nifty FMCG", "Nifty50 PR 2x Lev")),
        Map.entry("NSE@26020", List.of("Nifty Energy", "Nifty PSU Bank")),
        Map.entry("NSE@26034", List.of("Nifty Div Opps 50", "Nifty Metal")),
        Map.entry("NSE@26040", List.of("Nifty Commodities", "Nifty100 Liq 15")),
        Map.entry("NSE@26044", List.of("NIFTY MIDCAP 100", "Nifty50 TR 1x Inv")),
        Map.entry("NSE@26046", List.of("NIFTY SMLCAP 100", "Nifty Mid Liq 15"))
    );

    public static final Map<String, Entry> BY_SCRIP_KEY = Map.ofEntries(
        Map.entry("BSE@19000", new Entry("BSE@19000", "bse_cm", "SENSEX", "SENSEX")),
        Map.entry("BSE@19001", new Entry("BSE@19001", "bse_cm", "BSEPSU", "BSEPSU")),
        Map.entry("BSE@19002", new Entry("BSE@19002", "bse_cm", "BSE100", "BSE100")),
        Map.entry("BSE@19003", new Entry("BSE@19003", "bse_cm", "BSE200", "BSE200")),
        Map.entry("BSE@19004", new Entry("BSE@19004", "bse_cm", "BSE500", "BSE500")),
        Map.entry("BSE@19005", new Entry("BSE@19005", "bse_cm", "BSE IT", "BSE IT")),
        Map.entry("BSE@19006", new Entry("BSE@19006", "bse_cm", "BSEFMC", "BSEFMC")),
        Map.entry("BSE@19007", new Entry("BSE@19007", "bse_cm", "BSE CG", "BSE CG")),
        Map.entry("BSE@19008", new Entry("BSE@19008", "bse_cm", "BSE CD", "BSE CD")),
        Map.entry("BSE@19009", new Entry("BSE@19009", "bse_cm", "BSE HC", "BSE HC")),
        Map.entry("BSE@19011", new Entry("BSE@19011", "bse_cm", "TECK", "TECK")),
        Map.entry("BSE@19012", new Entry("BSE@19012", "bse_cm", "BANKEX", "BANKEX")),
        Map.entry("BSE@19013", new Entry("BSE@19013", "bse_cm", "AUTO", "AUTO")),
        Map.entry("BSE@19014", new Entry("BSE@19014", "bse_cm", "METAL", "METAL")),
        Map.entry("BSE@19015", new Entry("BSE@19015", "bse_cm", "CPSE", "CPSE")),
        Map.entry("BSE@19016", new Entry("BSE@19016", "bse_cm", "MIDCAP", "MIDCAP")),
        Map.entry("BSE@19017", new Entry("BSE@19017", "bse_cm", "SMLCAP", "SMLCAP")),
        Map.entry("BSE@19019", new Entry("BSE@19019", "bse_cm", "DOL100", "DOL100")),
        Map.entry("BSE@19020", new Entry("BSE@19020", "bse_cm", "DOL200", "DOL200")),
        Map.entry("BSE@19051", new Entry("BSE@19051", "bse_cm", "OILGAS", "OILGAS")),
        Map.entry("BSE@19052", new Entry("BSE@19052", "bse_cm", "POWER", "POWER")),
        Map.entry("BSE@19053", new Entry("BSE@19053", "bse_cm", "REALTY", "REALTY")),
        Map.entry("BSE@19054", new Entry("BSE@19054", "bse_cm", "BSEIPO", "BSEIPO")),
        Map.entry("BSE@19059", new Entry("BSE@19059", "bse_cm", "SMEIPO", "SMEIPO")),
        Map.entry("BSE@19060", new Entry("BSE@19060", "bse_cm", "INFRA", "INFRA")),
        Map.entry("BSE@19089", new Entry("BSE@19089", "bse_cm", "LMI250", "LMI250")),
        Map.entry("BSE@39", new Entry("BSE@39", "bse_cm", "ENERGY", "ENERGY")),
        Map.entry("BSE@40", new Entry("BSE@40", "bse_cm", "FINSER", "FINSER")),
        Map.entry("BSE@41", new Entry("BSE@41", "bse_cm", "INDSTR", "INDSTR")),
        Map.entry("BSE@42", new Entry("BSE@42", "bse_cm", "LRGCAP", "LRGCAP")),
        Map.entry("BSE@43", new Entry("BSE@43", "bse_cm", "MIDSEL", "MIDSEL")),
        Map.entry("BSE@44", new Entry("BSE@44", "bse_cm", "SMLSEL", "SMLSEL")),
        Map.entry("BSE@45", new Entry("BSE@45", "bse_cm", "TELCOM", "TELCOM")),
        Map.entry("BSE@47", new Entry("BSE@47", "bse_cm", "SNSX50", "SNSX50")),
        Map.entry("BSE@48", new Entry("BSE@48", "bse_cm", "SNXT50", "SNXT50")),
        Map.entry("BSE@55", new Entry("BSE@55", "bse_cm", "MID150", "MID150")),
        Map.entry("BSE@58", new Entry("BSE@58", "bse_cm", "MSL400", "MSL400")),
        Map.entry("NSE@25998", new Entry("NSE@25998", "nse_cm", "Nifty 500", "NIFTY 500")),
        Map.entry("NSE@26000", new Entry("NSE@26000", "nse_cm", "Nifty 50", "NIFTY 50")),
        Map.entry("NSE@26001", new Entry("NSE@26001", "nse_cm", "Nifty GrowSect 15", "NIFTY GROWSECT 15")),
        Map.entry("NSE@26008", new Entry("NSE@26008", "nse_cm", "Nifty IT", "NIFTY IT")),
        Map.entry("NSE@26009", new Entry("NSE@26009", "nse_cm", "Nifty Bank", "NIFTY BANK")),
        Map.entry("NSE@26012", new Entry("NSE@26012", "nse_cm", "Nifty 100", "NIFTY 100")),
        Map.entry("NSE@26013", new Entry("NSE@26013", "nse_cm", "Nifty Next 50", "NIFTY NEXT 50")),
        Map.entry("NSE@26014", new Entry("NSE@26014", "nse_cm", "Nifty Midcap 50", "NIFTY MIDCAP 50")),
        Map.entry("NSE@26017", new Entry("NSE@26017", "nse_cm", "India VIX", "INDIA VIX")),
        Map.entry("NSE@26018", new Entry("NSE@26018", "nse_cm", "Nifty Pharma", "NIFTY PHARMA")),
        Map.entry("NSE@26019", new Entry("NSE@26019", "nse_cm", "Nifty Infra", "NIFTY INFRA")),
        Map.entry("NSE@26021", new Entry("NSE@26021", "nse_cm", "Nifty Realty", "NIFTY REALTY")),
        Map.entry("NSE@26022", new Entry("NSE@26022", "nse_cm", "Nifty MNC", "NIFTY MNC")),
        Map.entry("NSE@26024", new Entry("NSE@26024", "nse_cm", "Nifty PSE", "NIFTY PSE")),
        Map.entry("NSE@26026", new Entry("NSE@26026", "nse_cm", "Nifty Serv Sector", "NIFTY SERV SECTOR")),
        Map.entry("NSE@26033", new Entry("NSE@26033", "nse_cm", "Nifty Auto", "NIFTY AUTO")),
        Map.entry("NSE@26035", new Entry("NSE@26035", "nse_cm", "Nifty Consumption", "NIFTY CONSUMPTION")),
        Map.entry("NSE@26036", new Entry("NSE@26036", "nse_cm", "Nifty 200", "NIFTY 200")),
        Map.entry("NSE@26037", new Entry("NSE@26037", "nse_cm", "Nifty Fin Service", "NIFTY FIN SERVICE")),
        Map.entry("NSE@26038", new Entry("NSE@26038", "nse_cm", "Nifty50 Div Point", "NIFTY50 DIV POINT")),
        Map.entry("NSE@26041", new Entry("NSE@26041", "nse_cm", "Nifty CPSE", "NIFTY CPSE")),
        Map.entry("NSE@26042", new Entry("NSE@26042", "nse_cm", "Nifty50 PR 1x Inv", "NIFTY50 PR 1X INV")),
        Map.entry("NSE@26043", new Entry("NSE@26043", "nse_cm", "Nifty50 TR 2x Lev", "NIFTY50 TR 2X LEV")),
        Map.entry("NSE@26048", new Entry("NSE@26048", "nse_cm", "NIFTY100 Qualty30", "NIFTY100 QUALTY30")),
        Map.entry("NSE@26049", new Entry("NSE@26049", "nse_cm", "Nifty GS 8 13Yr", "NIFTY GS 8 13YR")),
        Map.entry("NSE@26050", new Entry("NSE@26050", "nse_cm", "Nifty GS 10Yr", "NIFTY GS 10YR")),
        Map.entry("NSE@26051", new Entry("NSE@26051", "nse_cm", "Nifty GS 10Yr Cln", "NIFTY GS 10YR CLN")),
        Map.entry("NSE@26052", new Entry("NSE@26052", "nse_cm", "Nifty GS 4 8Yr", "NIFTY GS 4 8YR")),
        Map.entry("NSE@26053", new Entry("NSE@26053", "nse_cm", "Nifty GS 11 15Yr", "NIFTY GS 11 15YR")),
        Map.entry("NSE@26054", new Entry("NSE@26054", "nse_cm", "Nifty GS 15YrPlus", "NIFTY GS 15YRPLUS")),
        Map.entry("NSE@26055", new Entry("NSE@26055", "nse_cm", "Nifty GS Compsite", "NIFTY GS COMPSITE")),
        Map.entry("NSE@26056", new Entry("NSE@26056", "nse_cm", "NIFTY50 EQL Wgt", "NIFTY50 EQL WGT")),
        Map.entry("NSE@26057", new Entry("NSE@26057", "nse_cm", "NIFTY100 EQL Wgt", "NIFTY100 EQL WGT")),
        Map.entry("NSE@26058", new Entry("NSE@26058", "nse_cm", "NIFTY100 LowVol30", "NIFTY100 LOWVOL30")),
        Map.entry("NSE@26059", new Entry("NSE@26059", "nse_cm", "NIFTY Alpha 50", "NIFTY ALPHA 50")),
        Map.entry("NSE@26070", new Entry("NSE@26070", "nse_cm", "Nifty50 Value 20", "NIFTY50 VALUE 20")),
        Map.entry("NSE@26074", new Entry("NSE@26074", "nse_cm", "NIFTY MID SELECT", "NIFTY MID SELECT")),
        Map.entry("NSE@26076", new Entry("NSE@26076", "nse_cm", "Nifty Pvt Bank", "NIFTY PVT BANK")),
        Map.entry("NSE@26085", new Entry("NSE@26085", "nse_cm", "Nifty Media", "NIFTY MEDIA")),
        Map.entry("NSE@26090", new Entry("NSE@26090", "nse_cm", "NIFTY MIDCAP 150", "NIFTY MIDCAP 150")),
        Map.entry("NSE@26091", new Entry("NSE@26091", "nse_cm", "NIFTY SMLCAP 50", "NIFTY SMLCAP 50")),
        Map.entry("NSE@26092", new Entry("NSE@26092", "nse_cm", "NIFTY SMLCAP 250", "NIFTY SMLCAP 250")),
        Map.entry("NSE@26093", new Entry("NSE@26093", "nse_cm", "NIFTY MIDSML 400", "NIFTY MIDSML 400")),
        Map.entry("NSE@26094", new Entry("NSE@26094", "nse_cm", "NIFTY200 QUALTY30", "NIFTY200 QUALTY30"))
    );
}
