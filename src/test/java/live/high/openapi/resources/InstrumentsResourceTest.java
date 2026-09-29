// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import live.high.openapi.HighApiError;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.InstrumentFile;
import live.high.openapi.model.Instrument;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link InstrumentsResource} against real local servers (an
 * {@code HttpServer} where TLS is not the point of the test, an {@code
 * HttpsServer} where it is), per contract §11 — never a mocked transport.
 */
class InstrumentsResourceTest {

    private static final List<String> COLUMNS = List.of(
        "exchange", "segment", "instrument", "high_trading_symbol", "scrip_key", "isin", "scrip_code",
        "symbol", "name", "group_series", "has_fno", "underlying_symbol", "expiry", "option_type",
        "strike_price", "price_tick", "lot_size");

    private static final String CSV_HEADER = String.join(",", COLUMNS);

    // Equity row: quoted name with an embedded comma, four blank derivative-only columns.
    private static final String CSV_ROW_EQUITY =
        "NSE,ES,EQUITY,RELIANCE-EQ,NSE@2885,INE002A01018,2885,RELIANCE,"
            + "\"RELIANCE INDUSTRIES, LTD\",EQ,1,,,,,5,1";

    // Option row: exercises expiry/optionType/strikePrice/underlying, and an unnormalised priceTick.
    private static final String CSV_ROW_OPTION =
        "NSE,FO,OPTIDX,NIFTY-30OCT26-25000-CE,NSEFO@43210,,43210,NIFTY,NIFTY,,0,NIFTY,2026-10-30,CE,25000.50,5,75";

    private static final String CSV_BODY = CSV_HEADER + "\n" + CSV_ROW_EQUITY + "\n" + CSV_ROW_OPTION + "\n";

    private TestServer server;
    private TestServer tlsServer;

    @AfterEach
    void closeServers() {
        if (server != null) server.close();
        if (tlsServer != null) tlsServer.close();
    }

    // --- fixtures -----------------------------------------------------------

    private static String manifestJson(List<ManifestEntry> entries) {
        String columnsJson = COLUMNS.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(",", "[", "]"));
        String filesJson = entries.stream().map(ManifestEntry::toJson).collect(Collectors.joining(",", "[", "]"));
        return "{\"requestId\":\"r\",\"data\":{\"generatedAt\":\"2026-09-29T02:53:10.000Z\",\"columns\":"
            + columnsJson + ",\"files\":" + filesJson + "}}";
    }

    private record ManifestEntry(String instrument, String url) {
        String toJson() {
            return "{\"instrument\":\"" + instrument + "\",\"url\":\"" + url + "\",\"bytes\":123,\"rows\":2,"
                + "\"checksum\":\"deadbeef\",\"updatedAt\":\"2026-09-29T02:53:09.000Z\"}";
        }
    }

    private static void writeCsv(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "text/csv");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Trusts the throwaway self-signed certificate {@code TestServer#startTls} serves. Test-only. */
    private static HttpClient trustingHttpClient() throws Exception {
        TrustManager trustAll = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustAll}, new SecureRandom());
        return HttpClient.newBuilder().sslContext(sslContext).build();
    }

    /** {@code allowedHosts == null} keeps the default allowlist (the known CDN host). */
    private InstrumentsResource resourceOver(TestServer manifestServer, Set<String> allowedHosts) throws Exception {
        HighClientOptions.Builder options = HighClientOptions.builder()
            .baseUrl(manifestServer.baseUrl())
            .httpClient(trustingHttpClient());
        if (allowedHosts != null) options.allowedInstrumentHosts(allowedHosts);
        ResolvedConfig config = ResolvedConfig.resolve(options.build(), Map.of());
        return new InstrumentsResource(config);
    }

    // --- happy path: parsing correctness ------------------------------------

    @Test
    void streamsTypedRowsWithBlankFieldsAsNullAndPriceTickUnnormalised() throws Exception {
        tlsServer = TestServer.startTls((ex, i) -> {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        InstrumentsResource instruments = resourceOver(tlsServer, Set.of("127.0.0.1"));
        List<Instrument> rows;
        try (Stream<Instrument> stream = instruments.stream(InstrumentFile.InstrumentEnum.EQUITY)) {
            rows = stream.collect(Collectors.toList());
        }

        assertEquals(2, rows.size());
        Instrument equity = rows.get(0);
        assertEquals("NSE", equity.exchange());
        assertEquals("RELIANCE-EQ", equity.highTradingSymbol());
        assertEquals("RELIANCE INDUSTRIES, LTD", equity.name()); // the quoted, comma-containing field
        assertEquals(2885, equity.scripCode());
        assertEquals(1, equity.hasFnO());
        assertEquals(5, equity.priceTick()); // raw, not rescaled
        assertEquals(1, equity.lotSize());
        assertNull(equity.underlyingSymbol());
        assertNull(equity.expiry());
        assertNull(equity.optionType());
        assertNull(equity.strikePrice());

        Instrument option = rows.get(1);
        assertEquals(LocalDate.of(2026, 10, 30), option.expiry());
        assertEquals("CE", option.optionType());
        assertEquals(0, BigDecimal.valueOf(25000.50).compareTo(option.strikePrice()));
        assertTrue(option.strikePrice() instanceof BigDecimal);
        assertNull(option.isin());
        assertNull(option.groupSeries());
    }

    @Test
    void listCollectsTheSameRowsEagerly() throws Exception {
        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        List<Instrument> rows = resourceOver(tlsServer, Set.of("127.0.0.1")).list(InstrumentFile.InstrumentEnum.EQUITY);
        assertEquals(2, rows.size());
    }

    // --- no credentials, ever -----------------------------------------------

    @Test
    void sendsNoCredentialsToEitherTheManifestOrTheCsvDownloadEvenWhenTheClientHasThem() throws Exception {
        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        HighClientOptions options = HighClientOptions.builder()
            .baseUrl(tlsServer.baseUrl())
            .allowedInstrumentHosts(Set.of("127.0.0.1"))
            .httpClient(trustingHttpClient())
            .accessToken("SECRET-TOKEN")
            .apiKey("SECRET-KEY")
            .build();
        InstrumentsResource instruments = new InstrumentsResource(ResolvedConfig.resolve(options, Map.of()));

        instruments.list(InstrumentFile.InstrumentEnum.EQUITY);

        assertEquals(2, tlsServer.requests().size(), "expected exactly the manifest call and the CSV download");
        for (TestServer.ReceivedRequest request : tlsServer.requests()) {
            assertFalse(request.headers().containsKey("authorization"), request.path() + " must not carry Authorization");
            assertFalse(request.headers().containsKey("x-api-key"), request.path() + " must not carry x-api-key");
        }
    }

    // --- caching --------------------------------------------------------------

    @Test
    void cachesTheManifestAcrossCalls() throws Exception {
        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        InstrumentsResource instruments = resourceOver(tlsServer, Set.of("127.0.0.1"));
        instruments.list(InstrumentFile.InstrumentEnum.EQUITY);
        instruments.list(InstrumentFile.InstrumentEnum.EQUITY);

        long manifestCalls = tlsServer.requests().stream().filter(r -> r.path().equals("/v1/instruments")).count();
        assertEquals(1, manifestCalls, "the manifest must be fetched once and reused for the client's lifetime");
    }

    // --- Review Focus 1: unknown categories ignored, missing one is an error --

    @Test
    void ignoresAManifestCategoryTheSdkDoesNotKnowRatherThanFailingTheWholeManifest() throws Exception {
        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("warrants", tlsServer.baseUrl() + "/csv/warrants.csv"), // unknown to this SDK
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        List<Instrument> rows = resourceOver(tlsServer, Set.of("127.0.0.1")).list(InstrumentFile.InstrumentEnum.EQUITY);
        assertEquals(2, rows.size());
    }

    @Test
    void aRequestedCategoryTheManifestDoesNotListIsAClearError() throws Exception {
        server = TestServer.start((ex, i) ->
            TestServer.json(ex, 200, manifestJson(List.of(
                new ManifestEntry("equity", server.baseUrl() + "/csv/equity.csv")))));

        InstrumentsResource instruments = resourceOver(server, Set.of("127.0.0.1"));
        HighApiError error = assertThrows(HighApiError.class,
            () -> instruments.list(InstrumentFile.InstrumentEnum.DERIVATIVES));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("derivatives"));
        assertEquals(1, server.requests().size(), "must not attempt any download once the category lookup fails");
    }

    // --- Review Focus 2: CSV header must match the manifest's columns --------

    @Test
    void aCsvHeaderThatDoesNotMatchTheManifestsColumnsIsARefusalNotASilentShift() throws Exception {
        String wrongHeader = String.join(",", COLUMNS.subList(0, COLUMNS.size() - 1)); // one column short
        String badCsv = wrongHeader + "\n" + CSV_ROW_EQUITY + "\n";

        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, badCsv);
            }
        });

        InstrumentsResource instruments = resourceOver(tlsServer, Set.of("127.0.0.1"));
        HighApiError error = assertThrows(HighApiError.class, () -> instruments.list(InstrumentFile.InstrumentEnum.EQUITY));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("header"));
    }

    @Test
    void aMalformedRowFailsLoudlyRatherThanMisassigningFields() throws Exception {
        String badRow = "NSE,ES,EQUITY,RELIANCE-EQ,NSE@2885,INE002A01018,NOT-A-NUMBER,RELIANCE,"
            + "RELIANCE,EQ,1,,,,,5,1";
        String badCsv = CSV_HEADER + "\n" + badRow + "\n";

        tlsServer = TestServer.startTls((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
            } else {
                writeCsv(ex, badCsv);
            }
        });

        InstrumentsResource instruments = resourceOver(tlsServer, Set.of("127.0.0.1"));
        assertThrows(HighApiError.class, () -> instruments.list(InstrumentFile.InstrumentEnum.EQUITY));
    }

    // --- Review Focus 3: https-only and host allowlist, refused pre-flight ----

    @Test
    void refusesAnHttpDownloadUrlBeforeMakingAnyRequest() throws Exception {
        server = TestServer.start((ex, i) -> {
            if (ex.getRequestURI().getPath().equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", server.baseUrl() + "/csv/equity.csv")))); // http://, not https
            } else {
                writeCsv(ex, CSV_BODY);
            }
        });

        InstrumentsResource instruments = resourceOver(server, Set.of("127.0.0.1"));
        HighApiError error = assertThrows(HighApiError.class, () -> instruments.list(InstrumentFile.InstrumentEnum.EQUITY));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("https"));
        assertEquals(1, server.requests().size(), "must refuse before ever requesting the csv");
    }

    @Test
    void refusesADownloadHostOutsideTheAllowlistBeforeMakingAnyRequest() throws Exception {
        server = TestServer.start((ex, i) ->
            TestServer.json(ex, 200, manifestJson(List.of(
                new ManifestEntry("equity", "https://evil.example.com/csv")))));

        // Default allowlist (the known CDN host) — evil.example.com is not in it.
        InstrumentsResource instruments = resourceOver(server, null);
        HighApiError error = assertThrows(HighApiError.class, () -> instruments.list(InstrumentFile.InstrumentEnum.EQUITY));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("host"));
        assertEquals(1, server.requests().size());
    }

    // --- closing the stream closes the body -----------------------------------

    @Test
    void closingTheStreamClosesTheUnderlyingBody() throws Exception {
        CountDownLatch serverNoticedClose = new CountDownLatch(1);
        tlsServer = TestServer.startTls((ex, i) -> {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/v1/instruments")) {
                TestServer.json(ex, 200, manifestJson(List.of(
                    new ManifestEntry("equity", tlsServer.baseUrl() + "/csv/equity.csv"))));
                return;
            }
            try {
                ex.getResponseHeaders().add("content-type", "text/csv");
                ex.sendResponseHeaders(200, 0);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write((CSV_HEADER + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    for (int n = 0; n < 100; n++) {
                        os.write((CSV_ROW_EQUITY + "\n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        Thread.sleep(30);
                    }
                }
            } catch (IOException e) {
                // Exactly what we are testing for: the client closed its side.
                serverNoticedClose.countDown();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        InstrumentsResource instruments = resourceOver(tlsServer, Set.of("127.0.0.1"));
        try (Stream<Instrument> stream = instruments.stream(InstrumentFile.InstrumentEnum.EQUITY)) {
            List<Instrument> first = stream.limit(1).collect(Collectors.toList());
            assertEquals(1, first.size());
        } // try-with-resources closes the Stream, which must close the response body

        assertTrue(serverNoticedClose.await(3, TimeUnit.SECONDS),
            "the server never observed the client disconnect — the stream's onClose did not close the body");
    }
}
