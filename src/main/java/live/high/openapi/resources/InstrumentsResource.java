// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import live.high.openapi.HighApiError;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.InstrumentFile;
import live.high.openapi.generated.model.InstrumentsManifest;
import live.high.openapi.model.Instrument;

/**
 * The instrument list (the scrip master): every scrip HIGH knows, per
 * category, as {@link Instrument} rows.
 *
 * <p>{@link #stream} is the primary form. The {@code all} and {@code
 * derivatives} categories are 14 MB and 11 MB of CSV; {@link #stream} reads
 * and parses lazily off the response body and never buffers the file, while
 * {@link #list} collects the same rows into a {@link List} for a caller who
 * would rather pay that memory cost upfront. <b>Always use {@link #stream} in
 * try-with-resources</b> — closing the returned {@link Stream} closes the
 * underlying HTTP response body; failing to close it (or to exhaust the
 * stream, which closes it via {@link Stream#forEach} et al. not doing so
 * automatically either) leaks the connection.
 *
 * <pre>{@code
 * try (Stream<Instrument> equities = high.instruments().stream(InstrumentFile.InstrumentEnum.EQUITY)) {
 *     equities.filter(i -> "NSE".equals(i.exchange())).forEach(i -> ...);
 * }
 * }</pre>
 *
 * <p><b>No credentials, ever.</b> This needs none of the API key or bearer
 * token the rest of the SDK attaches, and must not send them even when the
 * client is configured with both: the file is served by a third-party CDN, and
 * leaking either credential to it would be a real leak, not a formality. Every
 * request this resource issues — including the internal call this class makes
 * to resolve where to download from — carries neither header.
 *
 * <p><b>Category</b> is {@link InstrumentFile.InstrumentEnum}: the generated
 * enum, not a hand-rolled parallel of it. Unlike {@code ExpiryType} (whose
 * spec enum is declared inline on a path parameter the generator does not
 * materialise a type for at all), this one *is* a property of a named,
 * generated schema, so OpenAPI Generator already emits {@code
 * InstrumentFile.InstrumentEnum} with exactly the values {@code ALL}, {@code
 * EQUITY}, {@code DERIVATIVES}, {@code COMMODITY}, {@code ETFS} — reusing it
 * directly is what contract §8 means by never widening a spec enum in a
 * facade signature: there is nothing to widen away from here, and a category
 * a future spec adds shows up the moment {@code scripts/regenerate.sh} is run
 * again, with no separate type to keep in sync.
 *
 * <p>The category-to-URL resolution happens internally and is cached for the
 * lifetime of this (thread-safe, shared-across-threads) resource; nothing
 * about that mechanism is part of the public surface.
 */
public final class InstrumentsResource extends BaseResource {

    private static final int COLUMN_COUNT = 17;
    private static final TypeReference<JsonNode> RAW_JSON = new TypeReference<>() {};

    private final Object manifestLock = new Object();
    private volatile InstrumentsManifest cachedManifest;

    public InstrumentsResource(ResolvedConfig config) {
        super(config);
    }

    /**
     * Streams every row for {@code category}, parsing lazily off the HTTP
     * response body. Uses the client's configured {@code timeoutMs} as the
     * budget for the whole download (not only its headers — see {@link
     * TimeoutInputStream}); for the larger categories on a slow connection,
     * {@link #stream(InstrumentFile.InstrumentEnum, int)} takes a per-call
     * override instead of raising the SDK's global default.
     *
     * <p>Always consume this in try-with-resources: closing the stream closes
     * the underlying response body.
     */
    public Stream<Instrument> stream(InstrumentFile.InstrumentEnum category) {
        return stream(category, config.timeoutMs());
    }

    /** As {@link #stream(InstrumentFile.InstrumentEnum)}, with a per-call timeout override in milliseconds. */
    public Stream<Instrument> stream(InstrumentFile.InstrumentEnum category, int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be a positive number, received " + timeoutMs + ".");
        }
        long deadlineNanos = System.nanoTime() + Duration.ofMillis(timeoutMs).toNanos();

        InstrumentFile file = fileFor(category);
        URI url = validatedDownloadUrl(file.getUrl());
        List<String> expectedColumns = manifestColumns();

        HttpResponse<InputStream> response = openDownload(url, deadlineNanos);
        int statusCode = response.statusCode();
        InputStream timeoutBody = new TimeoutInputStream(response.body(), deadlineNanos);

        if (statusCode < 200 || statusCode >= 300) {
            String snippet = readSnippetQuietly(timeoutBody);
            closeQuietly(timeoutBody);
            throw new HighApiError("Instrument list download failed: HTTP " + statusCode
                + (snippet.isEmpty() ? "" : (" — " + snippet)), statusCode, snippet);
        }

        Reader reader = new InputStreamReader(
            new BufferedInputStream(timeoutBody, 64 * 1024), StandardCharsets.UTF_8);
        Csv.RowReader rows = new Csv.RowReader(reader);

        List<String> header;
        try {
            header = rows.next();
        } catch (IOException e) {
            closeQuietly(timeoutBody);
            throw new HighApiError("Failed to read the instrument CSV header: " + e.getMessage(), statusCode, e);
        }
        if (header == null || !header.equals(expectedColumns)) {
            closeQuietly(timeoutBody);
            throw new HighApiError("The instrument CSV header does not match the manifest's columns — refusing to "
                + "guess field positions. Expected " + expectedColumns + " but the file started with " + header + ".",
                statusCode);
        }

        Iterator<Instrument> iterator = new Iterator<>() {
            private List<String> nextRow = advance();

            private List<String> advance() {
                try {
                    return rows.next();
                } catch (IOException e) {
                    throw new HighApiError("Failed reading the instrument CSV: " + e.getMessage(), statusCode, e);
                }
            }

            @Override
            public boolean hasNext() {
                return nextRow != null;
            }

            @Override
            public Instrument next() {
                List<String> row = nextRow;
                nextRow = advance();
                return toInstrument(row, statusCode);
            }
        };

        Spliterator<Instrument> spliterator =
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false).onClose(() -> closeQuietly(timeoutBody));
    }

    /** Collects every row for {@code category} into a {@link List}, using the client's default timeout. */
    public List<Instrument> list(InstrumentFile.InstrumentEnum category) {
        return list(category, config.timeoutMs());
    }

    /** As {@link #list(InstrumentFile.InstrumentEnum)}, with a per-call timeout override in milliseconds. */
    public List<Instrument> list(InstrumentFile.InstrumentEnum category, int timeoutMs) {
        try (Stream<Instrument> rows = stream(category, timeoutMs)) {
            return rows.collect(Collectors.toList());
        }
    }

    /**
     * {@link #stream(InstrumentFile.InstrumentEnum)} run on a background
     * thread. Unlike the other resources' {@code ...Async} forms, cancelling
     * this future does not abort an in-flight download — this resource's HTTP
     * calls do not go through {@link Transport}'s cancellable pipeline (see
     * the class Javadoc), so there is no in-flight exchange for a cancel to
     * reach. It exists to keep the resource-fetching call off the caller's
     * thread, matching the sync/async convention elsewhere in this SDK.
     */
    public CompletableFuture<Stream<Instrument>> streamAsync(InstrumentFile.InstrumentEnum category) {
        return CompletableFuture.supplyAsync(() -> stream(category));
    }

    /** {@link #list(InstrumentFile.InstrumentEnum)} run on a background thread. See {@link #streamAsync} for the cancellation caveat. */
    public CompletableFuture<List<Instrument>> listAsync(InstrumentFile.InstrumentEnum category) {
        return CompletableFuture.supplyAsync(() -> list(category));
    }

    // --- manifest ---------------------------------------------------------

    private InstrumentFile fileFor(InstrumentFile.InstrumentEnum category) {
        for (InstrumentFile file : manifest().getFiles()) {
            if (file.getInstrument() == category) return file;
        }
        throw new HighApiError(
            "The instrument list has no entry for category \"" + category.getValue() + "\".", 0);
    }

    private List<String> manifestColumns() {
        return manifest().getColumns();
    }

    /** Cached for the lifetime of this resource. Double-checked locking: a failed fetch is never cached, so the next call retries. */
    private InstrumentsManifest manifest() {
        InstrumentsManifest cached = cachedManifest;
        if (cached != null) return cached;
        synchronized (manifestLock) {
            if (cachedManifest == null) {
                cachedManifest = fetchManifest();
            }
            return cachedManifest;
        }
    }

    /**
     * Fetches the manifest and assembles it by hand rather than binding
     * {@code InstrumentsManifest} directly with one call: the generated
     * {@code InstrumentEnum.fromValue} throws on any value it does not
     * recognise, and Jackson's collection deserialisation would let one bad
     * {@code files[]} entry fail the whole manifest. Skipping just that entry
     * (Review Focus #1: unknown categories are ignored, not fatal) needs
     * per-entry control that binding the whole tree in one shot does not give.
     */
    private InstrumentsManifest fetchManifest() {
        JsonNode root = get("/instruments", Transport.AuthKind.NONE, RAW_JSON);
        ObjectMapper mapper = live.high.openapi.generated.JSON.getDefault().getMapper();

        List<String> columns = new ArrayList<>();
        JsonNode columnsNode = root.get("columns");
        if (columnsNode != null) {
            for (JsonNode c : columnsNode) columns.add(c.asText());
        }

        List<InstrumentFile> files = new ArrayList<>();
        JsonNode filesNode = root.get("files");
        if (filesNode != null) {
            for (JsonNode fileNode : filesNode) {
                try {
                    files.add(mapper.treeToValue(fileNode, InstrumentFile.class));
                } catch (Exception e) {
                    config.logger().warn(
                        "Ignoring an instrument list entry this SDK version does not recognise: " + e.getMessage(), null);
                }
            }
        }

        InstrumentsManifest manifest = new InstrumentsManifest().columns(columns).files(files);
        JsonNode generatedAtNode = root.get("generatedAt");
        if (generatedAtNode != null && generatedAtNode.isTextual()) {
            try {
                manifest.generatedAt(OffsetDateTime.parse(generatedAtNode.asText()));
            } catch (DateTimeParseException ignored) {
                // Cosmetic only — nothing in this resource reads generatedAt back.
            }
        }
        return manifest;
    }

    // --- download -----------------------------------------------------------

    /**
     * Refuses before any request is made: not {@code https}, or a host outside
     * {@link ResolvedConfig#allowedInstrumentHosts()}. The manifest names an
     * arbitrary URL, and honouring one blindly would let a compromised or
     * malformed manifest entry redirect this SDK's download anywhere.
     */
    private URI validatedDownloadUrl(URI url) {
        if (url == null) {
            throw new HighApiError("The instrument list entry has no download URL.", 0);
        }
        String scheme = url.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new HighApiError(
                "Refusing to download the instrument list from a non-https URL: " + url, 0);
        }
        String host = url.getHost();
        if (host == null || !config.allowedInstrumentHosts().contains(host.toLowerCase(Locale.ROOT))) {
            throw new HighApiError("Refusing to download the instrument list from an unrecognised host \""
                + host + "\". Add it via HighClientOptions.allowedInstrumentHosts(...) if this is expected.", 0);
        }
        return url;
    }

    private HttpResponse<InputStream> openDownload(URI url, long deadlineNanos) {
        long remainingMs = Math.max(1, Duration.ofNanos(deadlineNanos - System.nanoTime()).toMillis());
        HttpRequest request = HttpRequest.newBuilder(url)
            .GET()
            .header("accept", "text/csv")
            .header("user-agent", config.userAgent())
            .timeout(Duration.ofMillis(remainingMs))
            .build();
        // Deliberately no Authorization / x-api-key here: this is a third-party
        // CDN, and this client's credentials — if any are configured — must
        // never reach it. See the class Javadoc.
        try {
            return config.httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new HighApiError("Failed to download the instrument list: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HighApiError("Interrupted while downloading the instrument list", 0, e);
        }
    }

    private static String readSnippetQuietly(InputStream body) {
        try {
            byte[] bytes = body.readNBytes(200);
            return new String(bytes, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    // --- row parsing --------------------------------------------------------

    private Instrument toInstrument(List<String> row, int statusCode) {
        try {
            if (row.size() != COLUMN_COUNT) {
                throw new IllegalArgumentException(
                    "expected " + COLUMN_COUNT + " fields, got " + row.size() + ": " + row);
            }
            return new Instrument(
                blankToNull(row.get(0)),
                blankToNull(row.get(1)),
                blankToNull(row.get(2)),
                blankToNull(row.get(3)),
                blankToNull(row.get(4)),
                blankToNull(row.get(5)),
                parseInt(row.get(6)),
                blankToNull(row.get(7)),
                blankToNull(row.get(8)),
                blankToNull(row.get(9)),
                parseInt(row.get(10)),
                blankToNull(row.get(11)),
                parseDate(row.get(12)),
                blankToNull(row.get(13)),
                parseDecimal(row.get(14)),
                parseInt(row.get(15)),
                parseInt(row.get(16)));
        } catch (RuntimeException e) {
            throw new HighApiError("Malformed instrument CSV row: " + e.getMessage(), statusCode, row);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static Integer parseInt(String value) {
        String v = blankToNull(value);
        return v == null ? null : Integer.valueOf(v.trim());
    }

    private static BigDecimal parseDecimal(String value) {
        String v = blankToNull(value);
        return v == null ? null : new BigDecimal(v.trim());
    }

    private static LocalDate parseDate(String value) {
        String v = blankToNull(value);
        return v == null ? null : LocalDate.parse(v.trim());
    }
}
