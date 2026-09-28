// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggerTest {

    // --- redactUrl -------------------------------------------------------

    @Test
    void redactUrlMasksSensitiveQueryParameters() {
        String url = "https://openapi.high.live/v1/auth/generate-access-token?clientId=C1&tOtp=123456";
        String redacted = Loggers.redactUrl(url);
        assertTrue(redacted.contains("clientId=C1"));
        assertTrue(redacted.contains("tOtp=REDACTED"));
        assertFalse(redacted.contains("123456"));
    }

    @Test
    void redactUrlMasksEveryDocumentedSensitiveParam() {
        String url = "https://h.example/x?apiKey=a&accessToken=b&tokenId=c&stepToken=d&clientId=keep";
        String redacted = Loggers.redactUrl(url);
        assertTrue(redacted.contains("apiKey=REDACTED"));
        assertTrue(redacted.contains("accessToken=REDACTED"));
        assertTrue(redacted.contains("tokenId=REDACTED"));
        assertTrue(redacted.contains("stepToken=REDACTED"));
        assertTrue(redacted.contains("clientId=keep"));
    }

    @Test
    void redactUrlLeavesAUrlWithNoSensitiveParamsUnchanged() {
        String url = "https://openapi.high.live/v1/orders/list";
        assertEquals(url, Loggers.redactUrl(url));
    }

    @Test
    void redactUrlDoesNotThrowOnAnUnparseableValue() {
        assertEquals("not a url at all ???", Loggers.redactUrl("not a url at all ???"));
    }

    // --- redactBody --------------------------------------------------------

    @Test
    void redactBodyMasksSensitiveKeysAtAnyNestingDepth() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("otp", "999999");
        inner.put("keep", "visible");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", "hunter2");
        body.put("nested", inner);

        Object masked = Loggers.redactBody(body);
        assertTrue(masked instanceof String);
        String json = (String) masked;
        assertTrue(json.contains("REDACTED"));
        assertFalse(json.contains("hunter2"));
        assertFalse(json.contains("999999"));
        assertTrue(json.contains("visible"));
    }

    @Test
    void redactBodyPassesPrimitivesThroughUntouched() {
        assertEquals("hello", Loggers.redactBody("hello"));
        assertEquals(42, Loggers.redactBody(42));
        assertEquals(null, Loggers.redactBody(null));
    }

    @Test
    void redactBodyReplacesAnOversizedPayloadWithANote() {
        Map<String, Object> big = new LinkedHashMap<>();
        big.put("data", "x".repeat(3000));
        Object masked = Loggers.redactBody(big);
        assertTrue(masked instanceof String);
        assertTrue(((String) masked).startsWith("[truncated:"));
    }

    // --- createLogger / levels ---------------------------------------------

    @Test
    void defaultsToSilentPrintingNothing() {
        List<String> lines = new ArrayList<>();
        Logger logger = Loggers.create(LogLevel.SILENT, capturingSink(lines));
        logger.error("boom", null);
        logger.warn("careful", null);
        logger.info("fyi", null);
        logger.debug("detail", null);
        assertTrue(lines.isEmpty());
    }

    @Test
    void eachLevelPrintsItselfAndEverythingMoreSevere() {
        Logger warnLogger = Loggers.create(LogLevel.WARN, capturingSink(new ArrayList<>()));
        assertTrue(warnLogger.enabled(LogLevel.ERROR));
        assertTrue(warnLogger.enabled(LogLevel.WARN));
        assertFalse(warnLogger.enabled(LogLevel.INFO));
        assertFalse(warnLogger.enabled(LogLevel.DEBUG));
    }

    @Test
    void everyLinePrefixesAnIso8601TimestampAndThePaddedLevel() {
        List<String> lines = new ArrayList<>();
        Logger logger = Loggers.create(LogLevel.DEBUG, capturingSink(lines));
        logger.debug("hello", null);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z DEBUG .*hello.*"));
    }

    @Test
    void logLevelRejectsAnUnknownValueNamingTheValidOnes() {
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> LogLevel.fromString("verbose", "options.logLevel"));
        assertTrue(error.getMessage().contains("verbose"));
        assertTrue(error.getMessage().contains("silent"));
        assertTrue(error.getMessage().contains("debug"));
    }

    private static LogSink capturingSink(List<String> lines) {
        return new LogSink() {
            @Override
            public void error(String message, Object detail) {
                lines.add(detail == null ? message : message + " " + detail);
            }

            @Override
            public void warn(String message, Object detail) {
                lines.add(detail == null ? message : message + " " + detail);
            }

            @Override
            public void info(String message, Object detail) {
                lines.add(detail == null ? message : message + " " + detail);
            }

            @Override
            public void debug(String message, Object detail) {
                lines.add(detail == null ? message : message + " " + detail);
            }
        };
    }
}
