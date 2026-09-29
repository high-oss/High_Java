// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.feed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Owns the per-instrument delta merge (datafeed plan Phase 0: "Ticks are
 * deltas, not snapshots... The SDK must own the merge and hand the caller a
 * complete, typed snapshot") and turns one incoming tick object into the
 * {@link FeedEvent}s it produces — zero, one, or for a FULL-mode quote tick
 * carrying top-of-book fields, two (Phase 3: "one frame, two events").
 *
 * <p>{@code HighFeed} owns routing (which scrip key a tick belongs to, from
 * its own {@code e}/{@code tk} reverse lookup) and calls the {@code process*}
 * method matching the frame's {@code name}; this class only merges and
 * builds models. Thread-safe: each per-instrument state map is a {@link
 * ConcurrentHashMap}, matching {@code HighFeed}'s own "shared across
 * threads" contract.
 */
public final class TickProcessor {

    private final Map<String, Map<String, String>> quoteState = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> depthState = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> indexState = new ConcurrentHashMap<>();

    /** Merges one {@code "sf"} (market watch) tick and returns the events it produces: a {@link QuoteEvent} when quote fields changed, a {@link DepthEvent} (touchline) when top-of-book fields changed, both, or neither. */
    public List<FeedEvent> processQuoteTick(String scripKey, JsonNode node) {
        Map<String, String> merged = quoteState.computeIfAbsent(scripKey, k -> new ConcurrentHashMap<>());
        java.util.Set<String> presentQuote = new LinkedHashSet<>();
        java.util.Set<String> presentTob = new LinkedHashSet<>();
        mergeFields(node, merged, key -> {
            if (FeedFields.QUOTE_WIRE_KEYS.contains(key)) presentQuote.add(key);
            else if (FeedFields.TOP_OF_BOOK_WIRE_KEYS.contains(key)) presentTob.add(key);
        });

        List<FeedEvent> events = new ArrayList<>(2);
        if (!presentQuote.isEmpty()) {
            Quote quote = FeedModelBuilder.buildQuote(scripKey, merged);
            events.add(new QuoteEvent(quote, publicNames(presentQuote, FeedFields.QUOTE_WIRE_TO_PUBLIC)));
        }
        if (!presentTob.isEmpty()) {
            Depth depth = FeedModelBuilder.buildTouchlineDepth(scripKey, merged);
            events.add(new DepthEvent(depth, touchlineLabels(presentTob)));
        }
        return events;
    }

    /** Merges one {@code "dp"} (five-level depth) tick and returns a single {@link DepthEvent} if any of its fields changed, otherwise an empty list. */
    public List<FeedEvent> processDepthTick(String scripKey, JsonNode node) {
        Map<String, String> merged = depthState.computeIfAbsent(scripKey, k -> new ConcurrentHashMap<>());
        java.util.Set<String> present = new LinkedHashSet<>();
        mergeFields(node, merged, key -> {
            if (FeedFields.DEPTH_WIRE_KEYS.contains(key)) present.add(key);
        });
        if (present.isEmpty()) return List.of();
        Depth depth = FeedModelBuilder.buildFullDepth(scripKey, merged);
        java.util.Set<String> labels = present.stream()
            .map(TickProcessor::depthFieldLabel)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.of(new DepthEvent(depth, labels));
    }

    /** Merges one {@code "if"} (index) tick and returns a single {@link IndexEvent} if any of its fields changed, otherwise an empty list. */
    public List<FeedEvent> processIndexTick(String scripKey, JsonNode node) {
        Map<String, String> merged = indexState.computeIfAbsent(scripKey, k -> new ConcurrentHashMap<>());
        java.util.Set<String> present = new LinkedHashSet<>();
        mergeFields(node, merged, key -> {
            if (FeedFields.INDEX_WIRE_KEYS.contains(key)) present.add(key);
        });
        if (present.isEmpty()) return List.of();
        IndexQuote index = FeedModelBuilder.buildIndex(scripKey, merged);
        return List.of(new IndexEvent(index, publicNames(present, FeedFields.INDEX_WIRE_TO_PUBLIC)));
    }

    public void forgetQuote(String scripKey) {
        quoteState.remove(scripKey);
    }

    public void forgetDepth(String scripKey) {
        depthState.remove(scripKey);
    }

    public void forgetIndex(String scripKey) {
        indexState.remove(scripKey);
    }

    private static void mergeFields(JsonNode node, Map<String, String> merged, java.util.function.Consumer<String> onKnownField) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (FeedFields.ROUTING_KEYS.contains(key)) continue;
            JsonNode value = field.getValue();
            merged.put(key, value == null || value.isNull() ? null : value.asText());
            onKnownField.accept(key);
        }
    }

    private static java.util.Set<String> publicNames(java.util.Set<String> wireKeys, Map<String, String> wireToPublic) {
        java.util.Set<String> out = new LinkedHashSet<>();
        for (String key : wireKeys) {
            String publicName = wireToPublic.get(key);
            if (publicName != null) out.add(publicName);
        }
        return out;
    }

    private static java.util.Set<String> touchlineLabels(java.util.Set<String> wireKeys) {
        java.util.Set<String> out = new LinkedHashSet<>();
        for (String key : wireKeys) {
            out.add(switch (key) {
                case "bp" -> "bids[0].price";
                case "bq" -> "bids[0].quantity";
                case "sp" -> "asks[0].price";
                case "bs" -> "asks[0].quantity";
                default -> key;
            });
        }
        return out;
    }

    /** {@code "bids[n].price"} / {@code "asks[n].quantity"} / {@code "...orders"}, 0-indexed to match {@link Depth#bids()}/{@link Depth#asks()} directly — the plan's numbered wire suffixes never reach the public surface. */
    private static String depthFieldLabel(String wireKey) {
        if (wireKey.equals("bp")) return "bids[0].price";
        if (wireKey.equals("bq")) return "bids[0].quantity";
        if (wireKey.equals("sp")) return "asks[0].price";
        if (wireKey.equals("bs")) return "asks[0].quantity";
        if (wireKey.startsWith("bno")) return "bids[" + (Integer.parseInt(wireKey.substring(3)) - 1) + "].orders";
        if (wireKey.startsWith("sno")) return "asks[" + (Integer.parseInt(wireKey.substring(3)) - 1) + "].orders";
        if (wireKey.startsWith("bp")) return "bids[" + Integer.parseInt(wireKey.substring(2)) + "].price";
        if (wireKey.startsWith("bq")) return "bids[" + Integer.parseInt(wireKey.substring(2)) + "].quantity";
        if (wireKey.startsWith("sp")) return "asks[" + Integer.parseInt(wireKey.substring(2)) + "].price";
        if (wireKey.startsWith("bs")) return "asks[" + Integer.parseInt(wireKey.substring(2)) + "].quantity";
        return wireKey;
    }
}
