// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import live.high.openapi.feed.DepthEvent;
import live.high.openapi.feed.FeedEvent;
import live.high.openapi.feed.FeedListener;
import live.high.openapi.feed.IndexEvent;
import live.high.openapi.feed.QuoteEvent;
import live.high.openapi.feed.ScripKeyTranslator;
import live.high.openapi.feed.TickProcessor;

/**
 * The HIGH live datafeed — a separate client alongside {@link HighClient},
 * built from the same {@link HighClientOptions} and reusing the same
 * credentials, over a raw {@code java.net.http.WebSocket} (contract §9; no
 * new dependency, per datafeed plan Phase 3's Java row).
 *
 * <pre>{@code
 * HighFeed feed = new HighFeed(HighClientOptions.builder()
 *     .accessToken(accessToken)   // PRODUCTION is the default and the only option — see below
 *     .build());
 * feed.addListener(new FeedListener() {
 *     public void onQuote(QuoteEvent event) {
 *         System.out.println(event.quote().scripKey() + " " + event.quote().lastTradedPrice());
 *     }
 * });
 * feed.connect();
 * feed.subscribeQuotes(List.of("NSE@2885", "BSE@532540"));
 * feed.subscribeIndices(List.of("NSE@26000"));   // Nifty 50 — see IndexFeedMap
 * }</pre>
 *
 * <p><b>Production only.</b> The datafeed has no sandbox host; a client built
 * with {@code environment(Environment.SANDBOX)} — or resolved to it via {@code
 * HIGH_ENVIRONMENT}, per the same resolution order {@link ResolvedConfig}
 * documents — fails right here at construction rather than silently dialling
 * production or a sandbox host that does not exist.
 *
 * <p><b>Auth is a gate.</b> {@link #connect()} sends {@code {"type":"cn",
 * "sessionid":...}} and blocks until the acknowledgement arrives — nothing
 * else is ever sent ahead of it. A {@code "stat":"NotOk"} ack throws {@link
 * HighFeedError} and this client is never reconnected for it: the server will
 * keep refusing (see {@link HighFeedError} for how {@link
 * HighFeedError.Reason} is chosen). A transport failure — dropped connection,
 * timeout — is reconnected with backoff instead, re-authenticating and
 * resubscribing everything before {@link FeedListener#onConnected()} fires
 * again.
 *
 * <p><b>Subscription is by HIGH scrip key</b>, split by kind per the
 * coordinator's call on the plan's original {@code subscribe(keys, kind)}
 * shape: {@link #subscribeQuotes}, {@link #subscribeDepth} and {@link
 * #subscribeIndices} (plus their unsubscribe/snapshot counterparts) each
 * validate in their own direction — the index methods accept only keys
 * {@code live.high.openapi.feed.IndexFeedMap} carries, and the quote/depth
 * methods refuse a key that is in that table, naming the key either way (see
 * {@code ScripKeyTranslator}). The feed's own identifiers never appear in
 * this surface.
 *
 * <p><b>Thread-safe.</b> Every mutating call (connect, subscribe family,
 * close) synchronizes on this instance's own lock; a feed is meant to be
 * shared across threads.
 */
public final class HighFeed {

    private static final ObjectMapper MAPPER = live.high.openapi.generated.JSON.getDefault().getMapper();
    private static final long IDLE_TIMEOUT_MS = 45_000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final ResolvedConfig config;
    private final HttpClient httpClient;
    private final URI uri;
    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "high-feed");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket webSocket;
    /**
     * The {@link FrameListener} bound to {@link #webSocket}. Deactivated
     * before deliberately discarding a socket inside {@link #doConnect()}
     * (auth rejected, ack timeout) so that if {@code abort()} still delivers
     * an asynchronous {@code onClose}/{@code onError} afterward, it is a
     * no-op — otherwise an auth-gate rejection could race its own abort into
     * triggering {@link #handleDisconnect}, which would reconnect after a
     * failure the plan says must never be retried.
     */
    private volatile FrameListener currentListener;
    private volatile boolean userClosed;
    private volatile boolean connected;
    private volatile int maxScripPerConn = -1;
    private volatile int maxScripPerReq = -1;
    private volatile CompletableFuture<JsonNode> pendingAuthAck;
    private volatile long lastMessageAtMillis;
    private volatile ScheduledFuture<?> idleCheckTask;
    private int reconnectAttempt;
    /** Guards against a duplicate reconnect being scheduled when both {@code onError} and {@code onClose} fire for one failure, or when the idle check's manual trigger races a listener callback for the same drop. Cleared once a reconnect either succeeds or gives up for good. */
    private volatile boolean reconnecting;
    private final java.util.concurrent.atomic.AtomicInteger channelCounter = new java.util.concurrent.atomic.AtomicInteger(1);

    private final Set<String> quoteScripKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> depthScripKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> indexScripKeys = ConcurrentHashMap.newKeySet();

    private final Map<String, String> scripKeyToFeedId = new ConcurrentHashMap<>();
    private final Map<String, String> feedIdToScripKey = new ConcurrentHashMap<>();
    private final TickProcessor tickProcessor = new TickProcessor();

    private final List<FeedListener> listeners = new CopyOnWriteArrayList<>();
    private final SubmissionPublisher<FeedEvent> publisher = new SubmissionPublisher<>();

    public HighFeed(HighClientOptions options) {
        this(options, System.getenv());
    }

    /** Package-visible so tests can resolve against a fake environment map, matching {@code ResolvedConfig}'s own test seam. */
    HighFeed(HighClientOptions options, Map<String, String> env) {
        Objects.requireNonNull(options, "options");
        this.config = ResolvedConfig.resolve(options, env);
        if (config.environment() == Environment.SANDBOX) {
            throw new IllegalStateException("HighFeed has no sandbox host — the HIGH datafeed is production-only. "
                + "Build this client with Environment.PRODUCTION (the default) or leave `environment` unset; "
                + "there is no substitute sandbox feed to fall back to.");
        }
        this.httpClient = config.httpClient();
        this.uri = URI.create(config.wsBaseUrl());
    }

    // --- lifecycle ------------------------------------------------------------

    /** Opens the socket, sends the auth frame, and blocks until its acknowledgement arrives. Throws {@link HighFeedError} on rejection or transport failure. */
    public void connect() {
        synchronized (lock) {
            if (connected || webSocket != null) {
                throw new IllegalStateException("HighFeed.connect() was already called on this instance.");
            }
            userClosed = false;
            doConnect();
            connected = true;
            lastMessageAtMillis = System.currentTimeMillis();
            scheduleIdleCheck();
            config.logger().info("HIGH datafeed connected (maxScripPerConn=" + maxScripPerConn
                + ", maxScripPerReq=" + maxScripPerReq + ")", null);
        }
    }

    /** Opens a socket, sends the auth frame, and waits for its ack — used by both {@link #connect()} and reconnection. Leaves {@code webSocket}/{@code maxScripPer*} set on success; cleans up and throws on failure. */
    private void doConnect() {
        CompletableFuture<JsonNode> ackFuture = new CompletableFuture<>();
        this.pendingAuthAck = ackFuture;

        WebSocket ws;
        FrameListener listener = new FrameListener();
        try {
            ws = httpClient.newWebSocketBuilder()
                .buildAsync(uri, listener)
                .get(config.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw HighFeedError.transport("Timed out opening the HIGH datafeed socket after " + config.timeoutMs() + "ms.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw HighFeedError.transport("Interrupted while opening the HIGH datafeed socket.", e);
        } catch (ExecutionException e) {
            throw HighFeedError.transport("Failed to open the HIGH datafeed socket: " + rootMessage(e), e.getCause());
        }
        this.webSocket = ws;
        this.currentListener = listener;

        sendAuthFrame(ws);

        JsonNode ack;
        try {
            ack = ackFuture.get(config.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            discardSocket(listener, ws);
            throw HighFeedError.transport(
                "Timed out waiting " + config.timeoutMs() + "ms for the HIGH datafeed auth acknowledgement.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            discardSocket(listener, ws);
            throw HighFeedError.transport("Interrupted while waiting for the HIGH datafeed auth acknowledgement.", e);
        } catch (ExecutionException e) {
            discardSocket(listener, ws);
            throw HighFeedError.transport("Failed while waiting for the HIGH datafeed auth acknowledgement: "
                + rootMessage(e), e.getCause());
        }

        String stat = ack.path("stat").asText(null);
        if (!"Ok".equals(stat)) {
            Integer stCode = ack.hasNonNull("stCode") ? ack.get("stCode").asInt() : null;
            String msg = ack.path("msg").asText(null);
            discardSocket(listener, ws);
            HighFeedError error = HighFeedError.authRejected(stCode, msg);
            config.logger().error("HIGH datafeed auth rejected"
                + (stCode != null ? " (stCode " + stCode + ")" : "") + ": " + msg, null);
            throw error;
        }

        int fromAckConn = ack.path("maxScripPerConn").asInt(-1);
        int fromAckReq = ack.path("maxScripPerReq").asInt(-1);
        this.maxScripPerConn = fromAckConn > 0 ? fromAckConn : Integer.MAX_VALUE;
        this.maxScripPerReq = fromAckReq > 0 ? fromAckReq : Integer.MAX_VALUE;
    }

    private void sendAuthFrame(WebSocket ws) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "cn");
        String accessToken = config.accessToken().orElseThrow(() -> new IllegalStateException(
            "HighFeed needs an accessToken. Pass it to HighClientOptions, or set HIGH_ACCESS_TOKEN."));
        node.put("sessionid", accessToken);
        // Deliberately never logs the node above — only this hand-built,
        // token-free stand-in reaches the logger, so the access token cannot
        // leak into a debug log however verbose the sink is configured.
        config.logger().debug("HIGH datafeed -> cn {\"type\":\"cn\",\"sessionid\":\"REDACTED\"}", null);
        ws.sendText(node.toString(), true);
    }

    public boolean isConnected() {
        return connected;
    }

    /** The connection's scrip ceiling, from the auth ack. {@code -1} before the first successful {@link #connect()}. */
    public int maxScripPerConn() {
        return maxScripPerConn;
    }

    /** The per-request scrip ceiling, from the auth ack. {@code -1} before the first successful {@link #connect()}. */
    public int maxScripPerReq() {
        return maxScripPerReq;
    }

    public HighFeed addListener(FeedListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public HighFeed removeListener(FeedListener listener) {
        listeners.remove(listener);
        return this;
    }

    /** Every tick, as a {@link FeedEvent}: {@link QuoteEvent}, {@link DepthEvent}, {@link IndexEvent}. */
    public Flow.Publisher<FeedEvent> publisher() {
        return publisher;
    }

    public void close() {
        synchronized (lock) {
            userClosed = true;
            cancelIdleCheck();
            WebSocket ws = webSocket;
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "client closed").get(2, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Best effort — the socket is being torn down either way.
                }
            }
            connected = false;
            webSocket = null;
            scheduler.shutdownNow();
            publisher.close();
        }
    }

    // --- subscription: quotes ---------------------------------------------

    public void subscribeQuotes(Collection<String> scripKeys) {
        subscribeInternal(Kind.QUOTE, scripKeys);
    }

    public void unsubscribeQuotes(Collection<String> scripKeys) {
        unsubscribeInternal(Kind.QUOTE, scripKeys);
    }

    public void snapshotQuotes(Collection<String> scripKeys) {
        snapshotInternal(Kind.QUOTE, scripKeys);
    }

    // --- subscription: depth ------------------------------------------------

    public void subscribeDepth(Collection<String> scripKeys) {
        subscribeInternal(Kind.DEPTH, scripKeys);
    }

    public void unsubscribeDepth(Collection<String> scripKeys) {
        unsubscribeInternal(Kind.DEPTH, scripKeys);
    }

    public void snapshotDepth(Collection<String> scripKeys) {
        snapshotInternal(Kind.DEPTH, scripKeys);
    }

    // --- subscription: indices ----------------------------------------------

    /** Accepts only scrip keys present in {@code live.high.openapi.feed.IndexFeedMap}; any other key is a named error. */
    public void subscribeIndices(Collection<String> scripKeys) {
        subscribeInternal(Kind.INDEX, scripKeys);
    }

    public void unsubscribeIndices(Collection<String> scripKeys) {
        unsubscribeInternal(Kind.INDEX, scripKeys);
    }

    public void snapshotIndices(Collection<String> scripKeys) {
        snapshotInternal(Kind.INDEX, scripKeys);
    }

    // --- subscription machinery ---------------------------------------------

    /** Mirrors {@code live.high.openapi.feed.FeedKind}, kept private here so the wire prefixes stay out of the public surface's import graph. */
    private enum Kind {
        QUOTE("mw"), DEPTH("dp"), INDEX("if");

        final String wirePrefix;

        Kind(String wirePrefix) {
            this.wirePrefix = wirePrefix;
        }
    }

    private Set<String> activeSetFor(Kind kind) {
        return switch (kind) {
            case QUOTE -> quoteScripKeys;
            case DEPTH -> depthScripKeys;
            case INDEX -> indexScripKeys;
        };
    }

    private void forgetState(Kind kind, String scripKey) {
        switch (kind) {
            case QUOTE -> tickProcessor.forgetQuote(scripKey);
            case DEPTH -> tickProcessor.forgetDepth(scripKey);
            case INDEX -> tickProcessor.forgetIndex(scripKey);
        }
    }

    private static String translate(Kind kind, String scripKey) {
        return kind == Kind.INDEX ? ScripKeyTranslator.translateIndex(scripKey) : ScripKeyTranslator.translateNonIndex(scripKey);
    }

    private void subscribeInternal(Kind kind, Collection<String> scripKeys) {
        List<String> keys = requireNonEmpty(scripKeys);
        List<String> feedIds = new ArrayList<>(keys.size());
        for (String key : keys) feedIds.add(translate(kind, key));

        synchronized (lock) {
            ensureConnected();
            Set<String> active = activeSetFor(kind);
            long newCount = keys.stream().filter(k -> !active.contains(k)).count();
            long total = totalActiveCount() + newCount;
            if (total > maxScripPerConn) {
                throw HighFeedError.limitExceeded("Subscribing to these " + keys.size() + " scrip(s) would bring the "
                    + "connection to " + total + " active subscriptions, over maxScripPerConn (" + maxScripPerConn + ").");
            }
            for (int i = 0; i < keys.size(); i++) {
                registerFeedId(keys.get(i), feedIds.get(i));
            }
            sendChunked(kind.wirePrefix + "s", feedIds);
            active.addAll(keys);
        }
    }

    private void unsubscribeInternal(Kind kind, Collection<String> scripKeys) {
        List<String> keys = requireNonEmpty(scripKeys);
        List<String> feedIds = new ArrayList<>(keys.size());
        for (String key : keys) feedIds.add(translate(kind, key));

        synchronized (lock) {
            ensureConnected();
            sendChunked(kind.wirePrefix + "u", feedIds);
            activeSetFor(kind).removeAll(keys);
            for (String key : keys) forgetState(kind, key);
            for (String key : keys) {
                if (!quoteScripKeys.contains(key) && !depthScripKeys.contains(key) && !indexScripKeys.contains(key)) {
                    String feedId = scripKeyToFeedId.remove(key);
                    if (feedId != null) feedIdToScripKey.remove(feedId);
                }
            }
        }
    }

    private void snapshotInternal(Kind kind, Collection<String> scripKeys) {
        List<String> keys = requireNonEmpty(scripKeys);
        List<String> feedIds = new ArrayList<>(keys.size());
        for (String key : keys) feedIds.add(translate(kind, key));

        synchronized (lock) {
            ensureConnected();
            for (int i = 0; i < keys.size(); i++) {
                registerFeedId(keys.get(i), feedIds.get(i));
            }
            sendChunked(kind.wirePrefix + "sp", feedIds);
        }
    }

    private void registerFeedId(String scripKey, String feedId) {
        scripKeyToFeedId.putIfAbsent(scripKey, feedId);
        feedIdToScripKey.putIfAbsent(feedId, scripKey);
    }

    private long totalActiveCount() {
        return quoteScripKeys.size() + depthScripKeys.size() + indexScripKeys.size();
    }

    /** Splits {@code feedIds} into {@link #maxScripPerReq}-sized requests, each its own {@code channelnum}. Caller must hold {@link #lock}. */
    private void sendChunked(String type, List<String> feedIds) {
        WebSocket ws = requireSocket();
        int chunkSize = maxScripPerReq > 0 ? maxScripPerReq : feedIds.size();
        for (int start = 0; start < feedIds.size(); start += chunkSize) {
            List<String> chunk = feedIds.subList(start, Math.min(start + chunkSize, feedIds.size()));
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", type);
            node.put("scrips", String.join("&", chunk));
            node.put("channelnum", channelCounter.getAndIncrement());
            String text = node.toString();
            config.logger().debug("HIGH datafeed -> " + type + " (" + chunk.size() + " scrips)", null);
            ws.sendText(text, true).exceptionally(ex -> {
                config.logger().error("HIGH datafeed: failed to send " + type + ": " + ex.getMessage(), null);
                return null;
            });
        }
    }

    private WebSocket requireSocket() {
        WebSocket ws = webSocket;
        if (ws == null) throw new IllegalStateException("HighFeed is not connected. Call connect() first.");
        return ws;
    }

    private void ensureConnected() {
        if (!connected) throw new IllegalStateException("HighFeed is not connected. Call connect() first.");
    }

    private static List<String> requireNonEmpty(Collection<String> scripKeys) {
        Objects.requireNonNull(scripKeys, "scripKeys");
        if (scripKeys.isEmpty()) throw new IllegalArgumentException("scripKeys must not be empty.");
        return List.copyOf(scripKeys);
    }

    // --- incoming frames -----------------------------------------------------

    /**
     * {@code java.net.http.WebSocket} delivers text in fragments — {@code
     * onText}'s {@code last} flag, not one call per message — so a large
     * frame silently loses data unless every fragment is accumulated first.
     * One instance per socket (a fresh listener per {@link #doConnect()}
     * call), so its buffer never mixes bytes across a reconnect.
     */
    private final class FrameListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        private volatile boolean active = true;

        /** Called when {@link #doConnect()} discards this socket deliberately (see {@link #discardSocket}) so a subsequent async {@code onClose}/{@code onError} from the abort is a no-op. */
        void deactivate() {
            active = false;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            // WebSocket.Listener does not auto-request once onOpen is overridden.
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            webSocket.request(1);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                lastMessageAtMillis = System.currentTimeMillis();
                try {
                    handleFrame(text);
                } catch (RuntimeException e) {
                    config.logger().error("HIGH datafeed: failed to handle an incoming frame: " + e.getMessage(), null);
                }
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (active) handleDisconnect("closed by server: " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (active) handleDisconnect("transport error: " + error.getMessage());
        }
    }

    /** Deliberately discards a socket this side is rejecting (an auth NotOk, or a timeout waiting for the ack) — not a transport failure to reconnect from. */
    private void discardSocket(FrameListener listener, WebSocket ws) {
        listener.deactivate();
        this.currentListener = null;
        closeQuietly(ws);
        this.webSocket = null;
    }

    private void handleFrame(String text) {
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (Exception e) {
            config.logger().warn("HIGH datafeed: received an unparsable frame (" + text.length() + " bytes)", null);
            return;
        }
        if (root.isArray()) {
            for (JsonNode element : root) handleFrameElement(element);
        } else if (root.isObject()) {
            handleFrameElement(root);
        }
    }

    private void handleFrameElement(JsonNode node) {
        String type = node.path("type").asText(null);
        if (type != null) {
            handleControlFrame(type, node);
            return;
        }
        routeTick(node);
    }

    private void handleControlFrame(String type, JsonNode node) {
        if ("cn".equals(type)) {
            CompletableFuture<JsonNode> pending = this.pendingAuthAck;
            if (pending != null && !pending.isDone()) pending.complete(node);
            return;
        }
        config.logger().debug("HIGH datafeed <- " + type + " ack: " + node.path("stat").asText(null), null);
    }

    private void routeTick(JsonNode node) {
        String name = node.path("name").asText(null);
        String e = node.path("e").asText(null);
        String tk = node.path("tk").asText(null);
        if (name == null || e == null || tk == null) {
            config.logger().debug("HIGH datafeed: ignoring a frame element with no name/e/tk", null);
            return;
        }
        String feedId = e + "|" + tk;
        String scripKey = feedIdToScripKey.get(feedId);
        if (scripKey == null) {
            // A tick for something we are no longer (or never were) tracking —
            // e.g. arriving just after unsubscribe. Not an error.
            return;
        }
        List<FeedEvent> events = switch (name) {
            case "sf" -> tickProcessor.processQuoteTick(scripKey, node);
            case "dp" -> tickProcessor.processDepthTick(scripKey, node);
            case "if" -> tickProcessor.processIndexTick(scripKey, node);
            default -> {
                config.logger().debug("HIGH datafeed: unrecognised tick frame \"" + name + "\"", null);
                yield List.<FeedEvent>of();
            }
        };
        for (FeedEvent event : events) dispatch(event);
    }

    private void dispatch(FeedEvent event) {
        if (event instanceof QuoteEvent quoteEvent) {
            for (FeedListener l : listeners) safeCall(() -> l.onQuote(quoteEvent));
        } else if (event instanceof DepthEvent depthEvent) {
            for (FeedListener l : listeners) safeCall(() -> l.onDepth(depthEvent));
        } else if (event instanceof IndexEvent indexEvent) {
            for (FeedListener l : listeners) safeCall(() -> l.onIndex(indexEvent));
        }
        offer(event);
    }

    private void offer(FeedEvent event) {
        publisher.offer(event, (subscriber, throwable) -> {
            config.logger().warn("HIGH datafeed: a Flow.Publisher subscriber is too slow; dropping an event", null);
            return false;
        });
    }

    private void safeCall(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            config.logger().error("HIGH datafeed: a FeedListener callback threw: " + e.getMessage(), null);
        }
    }

    // --- reconnection ----------------------------------------------------------

    private void handleDisconnect(String reason) {
        synchronized (lock) {
            boolean wasConnected = connected;
            connected = false;
            if (userClosed || reconnecting) return;
            reconnecting = true;
            if (wasConnected) {
                config.logger().warn("HIGH datafeed disconnected (" + reason + "); reconnecting", null);
                for (FeedListener l : listeners) safeCall(() -> l.onDisconnected(reason));
            }
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (userClosed) return;
        int attempt = reconnectAttempt++;
        long backoff = Math.min(MAX_BACKOFF_MS, (long) (250 * Math.pow(2, attempt)) + (long) (Math.random() * 100));
        config.logger().warn("HIGH datafeed: reconnecting in " + backoff + "ms (attempt " + (attempt + 1) + ")", null);
        scheduler.schedule(this::attemptReconnect, backoff, TimeUnit.MILLISECONDS);
    }

    private void attemptReconnect() {
        if (userClosed) return;
        synchronized (lock) {
            if (userClosed) return;
            webSocket = null;
            try {
                doConnect();
            } catch (HighFeedError e) {
                if (e.reason() == HighFeedError.Reason.TRANSPORT) {
                    scheduleReconnect();
                } else {
                    // Auth-gate failure on a reconnect attempt: never retried,
                    // matching a first connect()'s NotOk — the server will
                    // keep refusing.
                    reconnecting = false;
                    config.logger().error("HIGH datafeed: reconnect auth rejected, giving up: " + e.getMessage(), null);
                    for (FeedListener l : listeners) safeCall(() -> l.onError(e));
                }
                return;
            }
            connected = true;
            reconnecting = false;
            reconnectAttempt = 0;
            lastMessageAtMillis = System.currentTimeMillis();
            scheduleIdleCheck();
            resubscribeAll();
            config.logger().info("HIGH datafeed reconnected and resubscribed", null);
            for (FeedListener l : listeners) safeCall(l::onConnected);
        }
    }

    /** Caller must hold {@link #lock}. Resends every active subscription with fresh channel numbers before this reconnect reports itself connected. */
    private void resubscribeAll() {
        resubscribeKind(Kind.QUOTE, quoteScripKeys);
        resubscribeKind(Kind.DEPTH, depthScripKeys);
        resubscribeKind(Kind.INDEX, indexScripKeys);
    }

    private void resubscribeKind(Kind kind, Set<String> keys) {
        if (keys.isEmpty()) return;
        List<String> feedIds = new ArrayList<>(keys.size());
        for (String key : keys) {
            String feedId = scripKeyToFeedId.get(key);
            if (feedId == null) {
                feedId = translate(kind, key);
                registerFeedId(key, feedId);
            }
            feedIds.add(feedId);
        }
        if (keys.size() > maxScripPerConn) {
            config.logger().warn("HIGH datafeed: " + keys.size() + " active " + kind
                + " subscriptions exceed this reconnect's maxScripPerConn (" + maxScripPerConn
                + "); resubscribing anyway — the server may reject some.", null);
        }
        sendChunked(kind.wirePrefix + "s", feedIds);
    }

    // --- idle detection ----------------------------------------------------------

    private void scheduleIdleCheck() {
        cancelIdleCheck();
        idleCheckTask = scheduler.scheduleAtFixedRate(() -> {
            if (userClosed || !connected) return;
            long idleFor = System.currentTimeMillis() - lastMessageAtMillis;
            if (idleFor > IDLE_TIMEOUT_MS) {
                config.logger().warn("HIGH datafeed: no message received in " + idleFor + "ms; treating the connection as dead", null);
                WebSocket ws = webSocket;
                if (ws != null) ws.abort();
                handleDisconnect("idle timeout after " + idleFor + "ms");
            }
        }, IDLE_TIMEOUT_MS, Math.max(1000, IDLE_TIMEOUT_MS / 3), TimeUnit.MILLISECONDS);
    }

    private void cancelIdleCheck() {
        ScheduledFuture<?> task = idleCheckTask;
        if (task != null) task.cancel(false);
    }

    // --- misc ----------------------------------------------------------------

    private static void closeQuietly(WebSocket ws) {
        try {
            ws.abort();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    @Override
    public String toString() {
        // Deliberately omits the accessToken — see ResolvedConfig's toString().
        return "HighFeed{config=" + config + ", connected=" + connected + '}';
    }
}
