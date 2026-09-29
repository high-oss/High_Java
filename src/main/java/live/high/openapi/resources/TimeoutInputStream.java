// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds every {@code read()} against a single deadline for the whole
 * exchange, because {@code java.net.http}'s own {@code HttpRequest.timeout()}
 * does not: with {@code BodyHandlers.ofInputStream()}, the client considers
 * the response "complete" (and stops its own clock) the moment the {@code
 * InputStream} is handed back, headers-received — not once the body is fully
 * read. Left unwrapped, a connection that stalls mid-body after a fast
 * response head would hang a caller past the SDK's documented timeout,
 * exactly the "timeout that only covers headers" the instrument-list plan
 * calls out as the failure to avoid.
 *
 * <p>Each blocked read runs on a small daemon executor so it can be aborted
 * on a timeout ({@link Future#get(long, TimeUnit)}), rather than trusting the
 * underlying socket read to notice a deadline on its own.
 *
 * <p>Package-private: an implementation detail of {@link InstrumentsResource}.
 */
final class TimeoutInputStream extends FilterInputStream {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "high-sdk-instrument-download");
        t.setDaemon(true);
        return t;
    });

    private final long deadlineNanos;

    /** @param deadlineNanos an absolute {@link System#nanoTime()} deadline for every remaining byte. */
    TimeoutInputStream(InputStream in, long deadlineNanos) {
        super(in);
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public int read() throws IOException {
        return within(in::read);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return within(() -> in.read(b, off, len));
    }

    private int within(Callable<Integer> read) throws IOException {
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMs <= 0) {
            closeQuietly();
            throw new InterruptedIOException("Instrument list download timed out");
        }

        Future<Integer> future = EXECUTOR.submit(read);
        try {
            return future.get(remainingMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            closeQuietly();
            throw new InterruptedIOException(
                "Instrument list download timed out after " + remainingMs + "ms waiting on the body");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Failed reading the instrument list download", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while reading the instrument list download");
        }
    }

    private void closeQuietly() {
        try {
            in.close();
        } catch (IOException ignored) {
            // best effort — we are already failing
        }
    }
}
