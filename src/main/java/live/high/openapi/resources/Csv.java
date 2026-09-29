// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal streaming CSV reader for {@link InstrumentsResource}: quoted
 * fields, embedded commas inside them, a doubled {@code ""} as an escaped
 * quote, and {@code \r\n}/{@code \n} row terminators recognised only outside
 * quotes — exactly the dialect {@code toScripMasterCsv} on the gateway writes
 * (a field is quoted only when it contains a comma, a quote or a newline).
 *
 * <p>Not {@code String.split(",")}: the {@code name} column legitimately
 * contains commas inside quotes, and splitting on every comma would silently
 * shift every field after it into the wrong column.
 *
 * <p>Package-private: this is an implementation detail of {@link
 * InstrumentsResource}, not part of the SDK's public surface.
 */
final class Csv {

    private Csv() {}

    /** Reads one row at a time from a {@link Reader}, so a caller never buffers the whole file. */
    static final class RowReader {

        private static final int NONE = -2;

        private final Reader in;
        private int pushback = NONE;

        RowReader(Reader in) {
            this.in = in;
        }

        private int read() throws IOException {
            if (pushback != NONE) {
                int c = pushback;
                pushback = NONE;
                return c;
            }
            return in.read();
        }

        private void unread(int c) {
            pushback = c;
        }

        /** The next row's fields, or {@code null} at end of stream. */
        List<String> next() throws IOException {
            int c = read();
            if (c == -1) return null;

            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;

            while (true) {
                if (c == -1) {
                    fields.add(field.toString());
                    return fields;
                }
                if (quoted) {
                    if (c == '"') {
                        int next = read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) unread(next);
                            else {
                                fields.add(field.toString());
                                return fields;
                            }
                        }
                    } else {
                        field.append((char) c);
                    }
                } else if (c == '"' && field.length() == 0) {
                    quoted = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field = new StringBuilder();
                } else if (c == '\r') {
                    int next = read();
                    if (next != '\n' && next != -1) unread(next);
                    fields.add(field.toString());
                    return fields;
                } else if (c == '\n') {
                    fields.add(field.toString());
                    return fields;
                } else {
                    field.append((char) c);
                }
                c = read();
            }
        }
    }
}
