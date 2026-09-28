// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

/** Anything that can receive the SDK's log lines. {@code System.err}/{@code out} satisfy it via {@link Loggers#consoleSink()}. */
public interface LogSink {
    void error(String message, Object detail);

    void warn(String message, Object detail);

    void info(String message, Object detail);

    void debug(String message, Object detail);
}
