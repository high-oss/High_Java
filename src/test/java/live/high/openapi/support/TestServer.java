// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * A real HTTP server on a random port, backed by the JDK's own {@link
 * HttpServer} — no test dependency needed for it. Tests assert on actual
 * exchanges rather than on a mocked {@code HttpClient}, per contract §11.
 */
public final class TestServer implements AutoCloseable {

    /** One request the server received, recorded before the handler runs. */
    public record ReceivedRequest(String method, String path, Map<String, String> headers, String body) {}

    /** Handles one request. {@code index} is this request's 0-based order of arrival. */
    @FunctionalInterface
    public interface Handler {
        void handle(HttpExchange exchange, int index) throws IOException;
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final List<ReceivedRequest> requests = new ArrayList<>();
    private final boolean tls;
    private volatile boolean closed = false;

    private TestServer(HttpServer server, ExecutorService executor, boolean tls) {
        this.server = server;
        this.executor = executor;
        this.tls = tls;
    }

    public static TestServer start(Handler handler) throws IOException {
        return start(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0), handler, false);
    }

    /**
     * As {@link #start}, but over TLS with a throwaway self-signed certificate
     * for {@code 127.0.0.1} — for the one feature that refuses a plain {@code
     * http://} URL outright ({@code InstrumentsResource}'s downloaded-URL
     * safety check), a real "happy path" test needs a server that actually
     * speaks TLS, not just an {@code https://} string nothing connects to.
     * Pair this with {@code InstrumentsResourceTest#trustingHttpClient()} on
     * the client side, since the certificate is self-signed.
     */
    public static TestServer startTls(Handler handler) throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = TestServer.class.getResourceAsStream("/instrument-test-tls.p12")) {
            keyStore.load(in, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(com.sun.net.httpserver.HttpsParameters params) {
                params.setSSLParameters(sslContext.getDefaultSSLParameters());
            }
        });
        return start(server, handler, true);
    }

    private static TestServer start(HttpServer server, Handler handler, boolean tls) throws IOException {
        // A cached pool so a handler that sleeps (to simulate a hang or a
        // stalled body) never blocks any other request, in this test or another
        // running concurrently.
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-server");
            t.setDaemon(true);
            return t;
        });
        TestServer testServer = new TestServer(server, executor, tls);

        server.createContext("/", exchange -> {
            try {
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                Map<String, String> headers = new LinkedHashMap<>();
                exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), String.join(",", v)));
                int index;
                synchronized (testServer.requests) {
                    index = testServer.requests.size();
                    testServer.requests.add(new ReceivedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(),
                        headers,
                        new String(bodyBytes, StandardCharsets.UTF_8)));
                }
                handler.handle(exchange, index);
            } catch (IOException e) {
                // The exchange was abandoned (e.g. a "never responds" handler that
                // the client-side timeout gave up on first, then the test closed
                // the server). Nothing to do.
            }
        });
        server.setExecutor(executor);
        server.start();
        return testServer;
    }

    public String baseUrl() {
        return (tls ? "https://127.0.0.1:" : "http://127.0.0.1:") + server.getAddress().getPort();
    }

    public List<ReceivedRequest> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        server.stop(0);
        executor.shutdownNow();
    }

    /** Writes a JSON response with a 200-family status and no extra headers. */
    public static void json(HttpExchange exchange, int status, String jsonBody) throws IOException {
        jsonWithHeaders(exchange, status, jsonBody, Map.of());
    }

    public static void jsonWithHeaders(
        HttpExchange exchange, int status, String jsonBody, Map<String, String> extraHeaders
    ) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        extraHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Writes a non-JSON (e.g. HTML) response, simulating a proxy failure. */
    public static void text(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Sends a 204 with no body.
     *
     * <p>Forces {@code Connection: close}. Without it, {@code
     * HttpExchange#sendResponseHeaders(204, -1)} leaves the framing of "no
     * body" ambiguous enough over a kept-alive HTTP/1.1 connection that {@code
     * java.net.http}'s client can hang waiting for bytes that were never
     * going to arrive — an explicit close is the unambiguous end-of-body
     * signal.
     */
    public static void noContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("connection", "close");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    /**
     * Writes {@code firstChunk}, flushes so the client sees headers and a
     * partial body, then after {@code delayMs} writes {@code restOfBody} and
     * closes. Chunked transfer (length 0 requests chunked encoding).
     */
    public static void stalledBody(HttpExchange exchange, String firstChunk, long delayMs, String restOfBody)
        throws IOException {
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write(firstChunk.getBytes(StandardCharsets.UTF_8));
        os.flush();
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        os.write(restOfBody.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
}
