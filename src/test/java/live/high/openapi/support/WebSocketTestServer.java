// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A real WebSocket server on a random port — a hand-rolled RFC 6455
 * handshake and framing helper, in test scope only, per the datafeed plan's
 * instruction that {@code HighFeed}'s tests exercise a real socket exchange
 * rather than a mocked one. The JDK ships a WebSocket <em>client</em> ({@code
 * java.net.http.WebSocket}) but no server, so there is nothing off-the-shelf
 * to stand this up with.
 *
 * <p>Deliberately minimal: text frames only (the protocol here is JSON text),
 * one connection handled at a time per {@link Connection}, and just enough of
 * RFC 6455 to (a) mask/unmask correctly (clients mask, servers must not),
 * (b) reassemble a fragmented incoming message, and (c) send a message split
 * across several frames on purpose, for {@code HighFeed}'s own reassembly to
 * be exercised.
 */
public final class WebSocketTestServer implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final BlockingQueue<Connection> pending = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    private WebSocketTestServer(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.acceptThread = new Thread(this::acceptLoop, "ws-test-server-accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    public static WebSocketTestServer start() throws IOException {
        return new WebSocketTestServer(new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")));
    }

    public String url() {
        return "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/";
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                Connection conn = handshake(socket);
                if (conn != null) pending.add(conn);
            } catch (IOException e) {
                if (!closed) {
                    // Server socket torn down or a peer misbehaved during handshake — nothing a test can act on.
                }
            }
        }
    }

    private Connection handshake(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        Map<String, String> headers = readHttpHeaders(in);
        String key = headers.get("sec-websocket-key");
        if (key == null) {
            socket.close();
            return null;
        }
        String accept = computeAccept(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + accept + "\r\n"
            + "\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return new Connection(socket, in, out);
    }

    private static Map<String, String> readHttpHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder line = new StringBuilder();
        boolean sawRequestLine = false;
        int prev = -1;
        int c;
        while ((c = in.read()) != -1) {
            if (prev == '\r' && c == '\n') {
                String text = line.substring(0, line.length() - 1); // drop the trailing \r already appended
                if (text.isEmpty()) break; // blank line ends the header block
                if (!sawRequestLine) {
                    sawRequestLine = true;
                } else {
                    int colon = text.indexOf(':');
                    if (colon > 0) {
                        headers.put(text.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT),
                            text.substring(colon + 1).trim());
                    }
                }
                line.setLength(0);
                prev = -1;
                continue;
            }
            line.append((char) c);
            prev = c;
        }
        return headers;
    }

    private static String computeAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Blocks until a client has completed the WebSocket handshake, or fails the calling test. */
    public Connection acceptConnection(long timeoutMs) throws InterruptedException {
        Connection conn = pending.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (conn == null) throw new AssertionError("no client connected to " + url() + " within " + timeoutMs + "ms");
        return conn;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // best effort
        }
        acceptThread.interrupt();
    }

    /** One accepted, upgraded connection. All methods run on the calling (test) thread — there is no background reader. */
    public final class Connection implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        private Connection(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        /** Reads frames until a complete text message (fin=true) is assembled, and returns it. */
        public String receiveText(long timeoutMs) throws IOException {
            socket.setSoTimeout((int) timeoutMs);
            StringBuilder message = new StringBuilder();
            while (true) {
                Frame frame = readFrame();
                if (frame.opcode == 0x8) { // close
                    throw new IOException("peer sent a close frame before the expected text message");
                }
                if (frame.opcode == 0x9) { // ping — reply pong, keep waiting
                    writeFrame(0xA, frame.payload, false);
                    continue;
                }
                message.append(new String(frame.payload, StandardCharsets.UTF_8));
                if (frame.fin) return message.toString();
            }
        }

        /** Sends one unfragmented text frame. */
        public void sendText(String text) throws IOException {
            writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8), true);
        }

        /**
         * Sends {@code parts} as separate WebSocket frames of ONE logical
         * message — the first frame opcode TEXT with {@code fin=false}, each
         * middle frame opcode CONTINUATION with {@code fin=false}, and the
         * last frame opcode CONTINUATION with {@code fin=true}. Exercises the
         * client's own fragment-reassembly rather than this server's.
         */
        public void sendTextFragmented(String... parts) throws IOException {
            for (int i = 0; i < parts.length; i++) {
                int opcode = i == 0 ? 0x1 : 0x0;
                boolean fin = i == parts.length - 1;
                writeFrame(opcode, parts[i].getBytes(StandardCharsets.UTF_8), fin);
            }
        }

        /** Drops the raw TCP connection with no WebSocket close handshake — a transport failure, not a graceful close. */
        public void closeAbruptly() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort — that is the point of "abruptly"
            }
        }

        @Override
        public void close() throws IOException {
            try {
                writeFrame(0x8, new byte[0], true);
            } finally {
                socket.close();
            }
        }

        // --- RFC 6455 framing --------------------------------------------------

        private record Frame(boolean fin, int opcode, byte[] payload) {}

        private Frame readFrame() throws IOException {
            int b0 = readByteOrThrow();
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;

            int b1 = readByteOrThrow();
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) readByteOrThrow() << 8) | readByteOrThrow();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByteOrThrow();
            }

            byte[] maskKey = new byte[4];
            if (masked) {
                for (int i = 0; i < 4; i++) maskKey[i] = (byte) readByteOrThrow();
            }

            byte[] payload = readNBytes((int) len);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
                }
            }
            return new Frame(fin, opcode, payload);
        }

        /** Server-to-client frames must NOT be masked (RFC 6455 §5.1). */
        private void writeFrame(int opcode, byte[] payload, boolean fin) throws IOException {
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            header.write((fin ? 0x80 : 0x00) | (opcode & 0x0F));
            int len = payload.length;
            if (len < 126) {
                header.write(len);
            } else if (len <= 0xFFFF) {
                header.write(126);
                header.write((len >> 8) & 0xFF);
                header.write(len & 0xFF);
            } else {
                header.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) header.write((int) ((long) len >> shift) & 0xFF);
            }
            synchronized (out) {
                out.write(header.toByteArray());
                out.write(payload);
                out.flush();
            }
        }

        private int readByteOrThrow() throws IOException {
            int b = in.read();
            if (b == -1) throw new IOException("peer closed the connection mid-frame");
            return b;
        }

        private byte[] readNBytes(int n) throws IOException {
            byte[] buf = new byte[n];
            int off = 0;
            while (off < n) {
                int read = in.read(buf, off, n - off);
                if (read == -1) throw new IOException("peer closed the connection mid-payload");
                off += read;
            }
            return buf;
        }
    }
}
