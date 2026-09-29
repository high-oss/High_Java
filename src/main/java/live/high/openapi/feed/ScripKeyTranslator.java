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
 * dedicated methods, and each refuses the other's keys. A third case sits
 * alongside those two: a scrip key the scrip master itself maps to more than
 * one index (see {@link IndexFeedMap#AMBIGUOUS}) is rejected by both methods,
 * naming the candidates, rather than guessed at.
 *
 * <p>{@link #translateIndex} checks {@link IndexFeedMap#AMBIGUOUS} first,
 * then looks {@link IndexFeedMap#BY_SCRIP_KEY} up and nothing else: a key in
 * neither is rejected by name, even if its prefix would otherwise be valid —
 * it does not fall through to the prefix rule, because that is exactly the
 * silent-mistranslation risk (subscribing to a real token under an index's
 * identity) the coordinator's change exists to close.
 *
 * <p>{@link #translateNonIndex} is the reverse: it first rejects a key that
 * is ambiguous, then a key that unambiguously <em>is</em> an index — both
 * telling the caller to use the index methods — and only then applies the
 * prefix rule (Phase 1's key-prefix table), validated against the closed
 * {@link ScripKeyPrefix} enum. {@code MCX} (spot) and any prefix outside the
 * enum are rejected by name.
 */
public final class ScripKeyTranslator {

    private ScripKeyTranslator() {}

    /** Looks {@code scripKey} up in {@link IndexFeedMap} only. Throws {@link HighFeedError} — reason {@code AMBIGUOUS_SCRIP} if the scrip master maps it to more than one index, {@code UNSUPPORTED_SCRIP} if it names no index at all. */
    public static String translateIndex(String scripKey) {
        requireNonBlank(scripKey);
        rejectIfAmbiguous(scripKey);
        IndexFeedMap.Entry entry = IndexFeedMap.BY_SCRIP_KEY.get(scripKey);
        if (entry == null) {
            throw HighFeedError.unsupportedScrip("\"" + scripKey + "\" is not an index in the HIGH datafeed's index "
                + "table. Use subscribeQuotes/subscribeDepth for this scrip key instead of the index methods.");
        }
        return entry.feedSegment() + "|" + entry.feedSymbol();
    }

    /**
     * Rejects {@code scripKey} if it is ambiguous or is an (unambiguous)
     * index, then derives the feed segment from its own prefix. Throws
     * {@link HighFeedError} — reason {@code AMBIGUOUS_SCRIP}, or {@code
     * UNSUPPORTED_SCRIP} for an index key, an unrecognised prefix, or MCX
     * spot.
     */
    public static String translateNonIndex(String scripKey) {
        requireNonBlank(scripKey);
        rejectIfAmbiguous(scripKey);
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

    /** Throws {@link HighFeedError} (reason {@code AMBIGUOUS_SCRIP}) if the scrip master maps {@code scripKey} to more than one index. Checked before either lookup in both {@link #translateIndex} and {@link #translateNonIndex}. */
    private static void rejectIfAmbiguous(String scripKey) {
        List<String> candidates = IndexFeedMap.AMBIGUOUS.get(scripKey);
        if (candidates != null) {
            throw HighFeedError.ambiguousScrip("\"" + scripKey + "\" resolves to more than one index in the scrip "
                + "master: " + candidates + ". There is no way to disambiguate from the scrip key alone, so this "
                + "SDK refuses rather than guessing which one you mean.");
        }
    }

    private static void requireNonBlank(String scripKey) {
        Objects.requireNonNull(scripKey, "scripKey");
        if (scripKey.isBlank()) {
            throw HighFeedError.unsupportedScrip("A scrip key must not be blank.");
        }
    }
}
