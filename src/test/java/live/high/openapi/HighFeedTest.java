// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import live.high.openapi.feed.QuoteEvent;
import live.high.openapi.support.WebSocketTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HighFeed} against a real local WebSocket server ({@link
 * WebSocketTestServer}) rather than a mocked socket, per the datafeed plan's
 * testing instruction. Each test spins up its own server, since a raw socket
 * exchange is the point.
 */
class HighFeedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OK_ACK = "{\"stat\":\"Ok\",\"type\":\"cn\",\"msg\":\"successful\",\"stCode\":200,"
        + "\"maxScripPerConn\":500,\"maxScripPerReq\":200,\"sType\":\"v2.0\"}";

    private final ExecutorService clientThread = Executors.newCachedThreadPool();
    private final List<HighFeed> feedsToClose = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (HighFeed feed : feedsToClose) {
            try {
                feed.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        clientThread.shutdownNow();
    }

    private HighFeed feed(WebSocketTestServer server, HighClientOptions.Builder customize) {
        HighClientOptions options = customize
            .wsBaseUrl(server.url())
            .accessToken("test-access-token")
            .timeoutMs(4000)
            .build();
        HighFeed feed = new HighFeed(options, Map.of());
        feedsToClose.add(feed);
        return feed;
    }

    private HighFeed feed(WebSocketTestServer server) {
        return feed(server, HighClientOptions.builder());
    }

    private CompletableFuture<Void> connectAsync(HighFeed feed) {
        return CompletableFuture.runAsync(feed::connect, clientThread);
    }

    // --- sandbox refused at construction -------------------------------------

    @Test
    void sandboxIsRefusedAtConstruction() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new HighFeed(
            HighClientOptions.builder().environment(Environment.SANDBOX).accessToken("tok").build(), Map.of()));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("sandbox"));
    }

    @Test
    void productionIsFineAtConstruction() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            // environment(PRODUCTION) is explicit here but is also the default —
            // either way construction must not throw.
            HighFeed feed = feed(server, HighClientOptions.builder().environment(Environment.PRODUCTION));
            assertFalse(feed.isConnected());
        }
    }

    // --- auth-before-subscribe ordering, and the auth gate -------------------

    @Test
    void sendsAuthFirstAndBlocksSubscribeUntilTheAckArrives() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);

            // Subscribing before connect() must fail locally — never queued
            // ahead of the (not yet sent) auth frame.
            assertThrows(IllegalStateException.class, () -> feed.subscribeQuotes(List.of("NSE@2885")));

            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection conn = server.acceptConnection(3000);

            String firstFrame = conn.receiveText(3000);
            JsonNode firstNode = MAPPER.readTree(firstFrame);
            assertEquals("cn", firstNode.get("type").asText());
            assertEquals("test-access-token", firstNode.get("sessionid").asText());

            conn.sendText(OK_ACK);
            connecting.get(3000, TimeUnit.MILLISECONDS);
            assertTrue(feed.isConnected());
            assertEquals(500, feed.maxScripPerConn());
            assertEquals(200, feed.maxScripPerReq());

            feed.subscribeQuotes(List.of("NSE@2885"));
            String subscribeFrame = conn.receiveText(3000);
            JsonNode subscribeNode = MAPPER.readTree(subscribeFrame);
            assertEquals("mws", subscribeNode.get("type").asText());
            assertEquals("nse_cm|2885", subscribeNode.get("scrips").asText());
        }
    }

    // --- NotOk auth: named error, never retried -------------------------------

    @Test
    void notOkAuthThrowsANamedErrorAndNeverReconnects() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CompletableFuture<Void> connecting = connectAsync(feed);

            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000); // the "cn" auth frame
            conn.sendText("{\"stat\":\"NotOk\",\"type\":\"cn\",\"msg\":\"no active data plan subscription\",\"stCode\":11099}");

            ExecutionException wrapped = assertThrows(ExecutionException.class,
                () -> connecting.get(3000, TimeUnit.MILLISECONDS));
            assertTrue(wrapped.getCause() instanceof HighFeedError);
            HighFeedError error = (HighFeedError) wrapped.getCause();
            assertEquals(HighFeedError.Reason.NO_DATA_PLAN, error.reason());
            assertEquals(11099, error.stCode());
            assertFalse(feed.isConnected());

            // Never retried or reconnected: no second connection attempt shows up.
            assertThrows(AssertionError.class, () -> server.acceptConnection(600));
        }
    }

    @Test
    void notOkWithTheConfirmedMalformedRequestCodeIsClassifiedAsSuch() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CompletableFuture<Void> connecting = connectAsync(feed);

            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000);
            conn.sendText("{\"stat\":\"NotOk\",\"type\":\"cn\",\"msg\":\"invalid field count\",\"stCode\":11002}");

            ExecutionException wrapped = assertThrows(ExecutionException.class,
                () -> connecting.get(3000, TimeUnit.MILLISECONDS));
            HighFeedError error = (HighFeedError) wrapped.getCause();
            assertEquals(HighFeedError.Reason.MALFORMED_REQUEST, error.reason());
        }
    }

    @Test
    void notOkMentioningAnInvalidTokenIsClassifiedAsSuch() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CompletableFuture<Void> connecting = connectAsync(feed);

            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000);
            conn.sendText("{\"stat\":\"NotOk\",\"type\":\"cn\",\"msg\":\"session token is invalid or expired\",\"stCode\":11001}");

            ExecutionException wrapped = assertThrows(ExecutionException.class,
                () -> connecting.get(3000, TimeUnit.MILLISECONDS));
            HighFeedError error = (HighFeedError) wrapped.getCause();
            assertEquals(HighFeedError.Reason.INVALID_TOKEN, error.reason());
        }
    }

    // --- both limits honoured --------------------------------------------------

    @Test
    void splitsALargeSubscriptionAcrossRequestsPerMaxScripPerReq() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000);
            // A small maxScripPerReq (2) so five keys must split into three requests.
            conn.sendText("{\"stat\":\"Ok\",\"type\":\"cn\",\"msg\":\"successful\",\"stCode\":200,"
                + "\"maxScripPerConn\":100,\"maxScripPerReq\":2,\"sType\":\"v2.0\"}");
            connecting.get(3000, TimeUnit.MILLISECONDS);

            feed.subscribeQuotes(List.of("NSE@1", "NSE@2", "NSE@3", "NSE@4", "NSE@5"));

            int totalScrips = 0;
            for (int i = 0; i < 3; i++) {
                JsonNode node = MAPPER.readTree(conn.receiveText(3000));
                assertEquals("mws", node.get("type").asText());
                String[] scrips = node.get("scrips").asText().split("&");
                assertTrue(scrips.length <= 2, "each request must carry at most maxScripPerReq scrips");
                totalScrips += scrips.length;
            }
            assertEquals(5, totalScrips);
        }
    }

    @Test
    void refusesASubscriptionThatWouldExceedMaxScripPerConn() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000);
            conn.sendText("{\"stat\":\"Ok\",\"type\":\"cn\",\"msg\":\"successful\",\"stCode\":200,"
                + "\"maxScripPerConn\":3,\"maxScripPerReq\":10,\"sType\":\"v2.0\"}");
            connecting.get(3000, TimeUnit.MILLISECONDS);

            HighFeedError error = assertThrows(HighFeedError.class,
                () -> feed.subscribeQuotes(List.of("NSE@1", "NSE@2", "NSE@3", "NSE@4")));
            assertEquals(HighFeedError.Reason.LIMIT_EXCEEDED, error.reason());

            // Refused locally — the server never even saw a subscribe frame for it.
            assertThrows(java.io.IOException.class, () -> conn.receiveText(300));
        }
    }

    // --- fragmented frames ------------------------------------------------------

    @Test
    void reassemblesAFragmentedTextFrameBeforeParsing() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            List<QuoteEvent> received = new CopyOnWriteArrayList<>();
            CountDownLatch gotQuote = new CountDownLatch(1);
            feed.addListener(new live.high.openapi.feed.FeedListener() {
                @Override
                public void onQuote(QuoteEvent event) {
                    received.add(event);
                    gotQuote.countDown();
                }
            });

            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            conn.receiveText(3000);

            // The ack itself arrives split across three WebSocket frames.
            conn.sendTextFragmented(
                "{\"stat\":\"Ok\",\"type\":\"cn\",\"msg\":\"succ",
                "essful\",\"stCode\":200,\"maxScripPerConn\":500,",
                "\"maxScripPerReq\":200,\"sType\":\"v2.0\"}");
            connecting.get(3000, TimeUnit.MILLISECONDS);
            assertTrue(feed.isConnected());

            feed.subscribeQuotes(List.of("NSE@15563"));
            conn.receiveText(3000); // the mws subscribe frame

            // A tick, also split across frames, large enough that dropping a
            // fragment would silently lose the trailing fields.
            String tick = "[{\"e\":\"nse_cm\",\"tk\":\"15563\",\"name\":\"sf\",\"ts\":\"RELIANCE-EQ\",\"ltp\":\"1905.65\"}]";
            int mid = tick.length() / 2;
            conn.sendTextFragmented(tick.substring(0, mid), tick.substring(mid));

            assertTrue(gotQuote.await(3000, TimeUnit.MILLISECONDS), "expected the fragmented tick to be reassembled and delivered");
            assertEquals("NSE@15563", received.get(0).quote().scripKey());
            assertEquals("RELIANCE-EQ", received.get(0).quote().tradingSymbol());
            assertEquals(new java.math.BigDecimal("1905.65"), received.get(0).quote().lastTradedPrice());
        }
    }

    // --- reconnect and resubscribe ----------------------------------------------

    @Test
    void reconnectsAndResubscribesEverythingAfterATransportFailure() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            HighFeed feed = feed(server);
            CountDownLatch reconnected = new CountDownLatch(1);
            List<String> disconnectReasons = new CopyOnWriteArrayList<>();
            feed.addListener(new live.high.openapi.feed.FeedListener() {
                @Override
                public void onConnected() {
                    reconnected.countDown();
                }

                @Override
                public void onDisconnected(String reason) {
                    disconnectReasons.add(reason);
                }
            });

            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection first = server.acceptConnection(3000);
            first.receiveText(3000);
            first.sendText(OK_ACK);
            connecting.get(3000, TimeUnit.MILLISECONDS);

            feed.subscribeQuotes(List.of("NSE@2885", "BSE@532540"));
            first.receiveText(3000); // the mws frame

            // Simulate a network blip: drop the raw connection with no close handshake.
            first.closeAbruptly();

            WebSocketTestServer.Connection second = server.acceptConnection(6000);
            JsonNode authAgain = MAPPER.readTree(second.receiveText(3000));
            assertEquals("cn", authAgain.get("type").asText());
            second.sendText(OK_ACK);

            JsonNode resub = MAPPER.readTree(second.receiveText(3000));
            assertEquals("mws", resub.get("type").asText());
            java.util.Set<String> resubscribed = java.util.Set.of(resub.get("scrips").asText().split("&"));
            assertEquals(java.util.Set.of("nse_cm|2885", "bse_cm|532540"), resubscribed);

            assertTrue(reconnected.await(3000, TimeUnit.MILLISECONDS), "expected onConnected() after resubscribing");
            assertTrue(feed.isConnected());
            assertFalse(disconnectReasons.isEmpty());
        }
    }

    // --- credentials never leak -------------------------------------------------

    @Test
    void theAccessTokenNeverAppearsInLogsOrToString() throws Exception {
        try (WebSocketTestServer server = WebSocketTestServer.start()) {
            List<String> loggedLines = new CopyOnWriteArrayList<>();
            LogSink capturingSink = new LogSink() {
                @Override
                public void error(String message, Object detail) {
                    loggedLines.add(message + " " + detail);
                }

                @Override
                public void warn(String message, Object detail) {
                    loggedLines.add(message + " " + detail);
                }

                @Override
                public void info(String message, Object detail) {
                    loggedLines.add(message + " " + detail);
                }

                @Override
                public void debug(String message, Object detail) {
                    loggedLines.add(message + " " + detail);
                }
            };

            HighFeed feed = feed(server, HighClientOptions.builder().logLevel(LogLevel.DEBUG).logSink(capturingSink));

            CompletableFuture<Void> connecting = connectAsync(feed);
            WebSocketTestServer.Connection conn = server.acceptConnection(3000);
            String authFrameText = conn.receiveText(3000);
            assertTrue(authFrameText.contains("test-access-token"), "sanity check: the raw wire frame does carry the token");
            conn.sendText(OK_ACK);
            connecting.get(3000, TimeUnit.MILLISECONDS);

            feed.subscribeQuotes(List.of("NSE@2885"));
            conn.receiveText(3000);

            String printed = feed.toString();
            assertFalse(printed.contains("test-access-token"));

            for (String line : loggedLines) {
                assertFalse(line.contains("test-access-token"), "log line leaked the access token: " + line);
            }
        }
    }
}
