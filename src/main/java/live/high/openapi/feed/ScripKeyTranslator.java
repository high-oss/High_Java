// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import live.high.openapi.HighFeedError;

/**
 * Translates a HIGH scrip key into the feed's own {@code segment|value}
 * identifier — datafeed plan Phase 1 — and enforces the split the coordinator
 * asked for on top of it: index keys and non-index keys go through separate,
 * dedicated methods, and each refuses the other's keys.
 *
 * <p>{@link #translateIndex} looks {@link IndexFeedMap} up and nothing else:
 * a key absent from the table is rejected by name, even if its prefix would
 * otherwise be valid — it does not fall through to the prefix rule, because
 * that is exactly the silent-mistranslation risk (subscribing to a real
 * token under an index's identity) the coordinator's change exists to close.
 *
 * <p>{@link #translateNonIndex} is the reverse: it first rejects a key that
 * <em>is</em> in {@link IndexFeedMap}, telling the caller to use the index
 * methods, and only then applies the prefix rule (Phase 1's key-prefix
 * table), validated against the closed {@link ScripKeyPrefix} enum. {@code
 * MCX} (spot) and any prefix outside the enum are rejected by name.
 */
public final class ScripKeyTranslator {

    private ScripKeyTranslator() {}

    /** Looks {@code scripKey} up in {@link IndexFeedMap} only. Throws {@link HighFeedError} (reason {@code UNSUPPORTED_SCRIP}) if it is not there. */
    public static String translateIndex(String scripKey) {
        requireNonBlank(scripKey);
        IndexFeedMap.Entry entry = IndexFeedMap.BY_SCRIP_KEY.get(scripKey);
        if (entry == null) {
            throw HighFeedError.unsupportedScrip("\"" + scripKey + "\" is not an index in the HIGH datafeed's index "
                + "table. Use subscribeQuotes/subscribeDepth for this scrip key instead of the index methods.");
        }
        return entry.feedSegment() + "|" + entry.feedSymbol();
    }

    /**
     * Rejects {@code scripKey} if it is an index, then derives the feed
     * segment from its own prefix. Throws {@link HighFeedError} (reason
     * {@code UNSUPPORTED_SCRIP}) for an index key, an unrecognised prefix, or
     * MCX spot.
     */
    public static String translateNonIndex(String scripKey) {
        requireNonBlank(scripKey);
        if (IndexFeedMap.BY_SCRIP_KEY.containsKey(scripKey)) {
            throw HighFeedError.unsupportedScrip("\"" + scripKey + "\" is an index. Use subscribeIndices/"
                + "unsubscribeIndices/snapshotIndices instead of the quote or depth methods.");
        }

        int at = scripKey.indexOf('@');
        if (at <= 0 || at == scripKey.length() - 1) {
            throw HighFeedError.unsupportedScrip(
                "\"" + scripKey + "\" is not a HIGH scrip key (expected \"<PREFIX>@<code>\").");
        }
        String prefix = scripKey.substring(0, at);
        String code = scripKey.substring(at + 1);

        if (ScripKeyPrefix.MCX_SPOT_PREFIX.equals(prefix)) {
            throw HighFeedError.unsupportedScrip("\"" + scripKey + "\": MCX spot has no known feed code — "
                + "the datafeed does not cover MCX cash/spot instruments.");
        }

        ScripKeyPrefix known = ScripKeyPrefix.byPrefix(prefix);
        if (known == null) {
            throw HighFeedError.unsupportedScrip("\"" + scripKey + "\": unrecognised scrip key prefix \"" + prefix
                + "\". Supported prefixes: " + ScripKeyPrefix.supportedPrefixes() + " (plus indices — see IndexFeedMap).");
        }
        return known.feedSegment() + "|" + code;
    }

    public static List<String> translateAllIndex(Collection<String> scripKeys) {
        List<String> out = new ArrayList<>(scripKeys.size());
        for (String key : scripKeys) out.add(translateIndex(key));
        return out;
    }

    public static List<String> translateAllNonIndex(Collection<String> scripKeys) {
        List<String> out = new ArrayList<>(scripKeys.size());
        for (String key : scripKeys) out.add(translateNonIndex(key));
        return out;
    }

    private static void requireNonBlank(String scripKey) {
        Objects.requireNonNull(scripKey, "scripKey");
        if (scripKey.isBlank()) {
            throw HighFeedError.unsupportedScrip("A scrip key must not be blank.");
        }
    }
}
