// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PathsTest {

    @Test
    void interpolatesAPlainParameter() {
        assertEquals(
            "/orders/2609250000123456",
            Paths.pathOf("/orders/{orderId}", Map.of("orderId", "2609250000123456")));
    }

    @Test
    void encodesCharactersThatWouldOtherwiseChangeThePath() {
        assertEquals(
            "/scrips/M%26M-EQ/depth",
            Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "M&M-EQ")));
        assertEquals(
            "/scrips/NIFTY%2050/depth",
            Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "NIFTY 50")));
    }

    @Test
    void encodesASlashSoAParameterCanNeverAddAPathSegment() {
        assertEquals(
            "/scrips/a%2F..%2Fb/depth",
            Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", "a/../b")));
    }

    @Test
    void interpolatesSeveralParameters() {
        assertEquals(
            "/scrips/NIFTY/OPT/expiries",
            Paths.pathOf("/scrips/{symbol}/{type}/expiries", Map.of("symbol", "NIFTY", "type", "OPT")));
    }

    @Test
    void acceptsANumericParameter() {
        assertEquals("/orders/42", Paths.pathOf("/orders/{orderId}", Map.of("orderId", 42)));
    }

    @Test
    void refusesATemplateWhoseParameterWasNotSupplied() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> Paths.pathOf("/orders/{orderId}", Map.of()));
        assertEquals(true, error.getMessage().contains("orderId"));
    }
}
