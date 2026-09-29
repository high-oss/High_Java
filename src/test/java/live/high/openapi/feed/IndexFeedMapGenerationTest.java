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
 * Rebuilds {@link IndexFeedMap#BY_SCRIP_KEY} and {@link
 * IndexFeedMap#AMBIGUOUS} from the checked-in copy of the canonical source
 * ({@code src/test/resources/index-feed-map.json} — see {@code
 * IndexFeedMap}'s Javadoc for why a copy ships here rather than reading the
 * file outside this repo) and asserts the committed tables still match —
 * datafeed plan Phase 1: "each SDK ships a test that rebuilds the join from
 * the committed source and asserts the generated table still matches. A new
 * index upstream then shows up as a failing test rather than a silent gap."
 *
 * <p>The canonical file is an object — {@code indices} (unambiguous rows)
 * and {@code ambiguous} (scrip keys the scrip master maps to more than one
 * index, deliberately excluded from {@code indices}) — not a flat array, so
 * this rebuild reads both arrays directly rather than re-deriving the split
 * itself.
 */
class IndexFeedMapGenerationTest {

    @Test
    void theCommittedIndexTableMatchesARebuildOfTheSourcesIndicesArray() throws IOException {
        JsonNode root = readCanonicalSource();
        Map<String, IndexFeedMap.Entry> expected = new LinkedHashMap<>();
        for (JsonNode node : root.get("indices")) {
            String scripKey = node.get("scripKey").asText();
            expected.put(scripKey, new IndexFeedMap.Entry(
                scripKey, node.get("feedSegment").asText(), node.get("feedSymbol").asText(), node.get("name").asText()));
        }

        assertFalse(expected.isEmpty(), "expected the checked-in index-feed-map.json fixture's indices array to be non-empty");
        assertEquals(expected, IndexFeedMap.BY_SCRIP_KEY);
    }

    @Test
    void theCommittedAmbiguousTableMatchesARebuildOfTheSourcesAmbiguousArray() throws IOException {
        JsonNode root = readCanonicalSource();
        Map<String, List<String>> expected = new LinkedHashMap<>();
        for (JsonNode node : root.get("ambiguous")) {
            List<String> candidates = new ArrayList<>();
            for (JsonNode candidate : node.get("candidates")) candidates.add(candidate.asText());
            expected.put(node.get("scripKey").asText(), List.copyOf(candidates));
        }

        assertEquals(6, expected.size());
        assertEquals(expected, IndexFeedMap.AMBIGUOUS);
    }

    @Test
    void noKeyAppearsInBothTables() {
        for (String key : IndexFeedMap.AMBIGUOUS.keySet()) {
            assertFalse(IndexFeedMap.BY_SCRIP_KEY.containsKey(key),
                key + " is ambiguous in the source and must not also resolve unambiguously");
        }
    }

    @Test
    void everyUnambiguousEntryIsReachableThroughTranslateIndex() {
        for (String scripKey : IndexFeedMap.BY_SCRIP_KEY.keySet()) {
            IndexFeedMap.Entry entry = IndexFeedMap.BY_SCRIP_KEY.get(scripKey);
            assertEquals(entry.feedSegment() + "|" + entry.feedSymbol(), ScripKeyTranslator.translateIndex(scripKey));
        }
        assertTrue(IndexFeedMap.BY_SCRIP_KEY.size() >= 80, "expected the bulk of the source's indices array to survive");
    }

    private static JsonNode readCanonicalSource() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = IndexFeedMapGenerationTest.class.getResourceAsStream("/index-feed-map.json")) {
            if (in == null) throw new IOException("src/test/resources/index-feed-map.json is missing");
            return mapper.readTree(in);
        }
    }
}
