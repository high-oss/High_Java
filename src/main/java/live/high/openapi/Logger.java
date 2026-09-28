// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

/** A {@link LogSink} that also knows what its own threshold is. */
public interface Logger extends LogSink {
    /** True when this level would print. Guard expensive detail computation with it. */
    boolean enabled(LogLevel level);
}
