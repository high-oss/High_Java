// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rebuilds {@link IndexFeedMap#BY_SCRIP_KEY} from the checked-in copy of the
 * canonical source ({@code src/test/resources/index-feed-map.json} — see
 * {@code IndexFeedMap}'s Javadoc for why a copy ships here rather than
 * reading the file outside this repo) and asserts the committed table still
 * matches — datafeed plan Phase 1: "each SDK ships a test that rebuilds the
 * join from the committed source and asserts the generated table still
 * matches. A new index upstream then shows up as a failing test rather than
 * a silent gap."
 *
 * <p>This rebuild also re-derives {@code IndexFeedMap}'s own documented
 * exclusion: six scrip keys in the canonical file are ambiguous (each one
 * appears twice, against two unrelated index names) and are excluded rather
 * than guessed at. If the canonical file is ever fixed upstream so a key
 * resolves to one row, this test starts failing — signalling that {@code
 * IndexFeedMap} can now pick that row up — rather than silently staying
 * stale.
 */
class IndexFeedMapGenerationTest {

    private record SourceRow(String scripKey, String feedSegment, String feedSymbol, String name) {}

    @Test
    void theCommittedTableMatchesARebuildFromTheCanonicalSource() throws IOException {
        List<SourceRow> rows = readCanonicalSource();
        assertFalse(rows.isEmpty(), "expected the checked-in index-feed-map.json fixture to be non-empty");

        Map<String, List<SourceRow>> byKey = new LinkedHashMap<>();
        for (SourceRow row : rows) {
            byKey.computeIfAbsent(row.scripKey(), k -> new ArrayList<>()).add(row);
        }

        Map<String, IndexFeedMap.Entry> expected = new LinkedHashMap<>();
        for (Map.Entry<String, List<SourceRow>> entry : byKey.entrySet()) {
            List<SourceRow> candidates = entry.getValue();
            if (candidates.size() > 1) continue; // ambiguous in the source — see IndexFeedMap's Javadoc
            SourceRow row = candidates.get(0);
            expected.put(row.scripKey(), new IndexFeedMap.Entry(row.scripKey(), row.feedSegment(), row.feedSymbol(), row.name()));
        }

        assertEquals(expected, IndexFeedMap.BY_SCRIP_KEY);
    }

    @Test
    void theSixAmbiguousKeysAreTheOnesDocumentedAndAreAbsentFromTheCommittedTable() throws IOException {
        List<SourceRow> rows = readCanonicalSource();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SourceRow row : rows) counts.merge(row.scripKey(), 1L, Long::sum);

        List<String> ambiguous = counts.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();

        assertEquals(List.of("NSE@26002", "NSE@26020", "NSE@26034", "NSE@26040", "NSE@26044", "NSE@26046"), ambiguous);
        for (String key : ambiguous) {
            assertFalse(IndexFeedMap.BY_SCRIP_KEY.containsKey(key), key + " is ambiguous in the source and must stay excluded");
        }
    }

    @Test
    void everyEntryIsReachableThroughTranslateIndex() {
        for (String scripKey : IndexFeedMap.BY_SCRIP_KEY.keySet()) {
            IndexFeedMap.Entry entry = IndexFeedMap.BY_SCRIP_KEY.get(scripKey);
            assertEquals(entry.feedSegment() + "|" + entry.feedSymbol(), ScripKeyTranslator.translateIndex(scripKey));
        }
        assertTrue(IndexFeedMap.BY_SCRIP_KEY.size() >= 80, "expected the bulk of the 93-row canonical file to survive");
    }

    private static List<SourceRow> readCanonicalSource() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = IndexFeedMapGenerationTest.class.getResourceAsStream("/index-feed-map.json")) {
            if (in == null) throw new IOException("src/test/resources/index-feed-map.json is missing");
            JsonNode root = mapper.readTree(in);
            List<SourceRow> rows = new ArrayList<>();
            for (JsonNode node : root) {
                rows.add(new SourceRow(
                    node.get("scripKey").asText(),
                    node.get("feedSegment").asText(),
                    node.get("feedSymbol").asText(),
                    node.get("name").asText()));
            }
            return rows;
        }
    }
}
