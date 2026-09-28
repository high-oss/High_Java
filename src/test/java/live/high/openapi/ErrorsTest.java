// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsAnErrorEnvelopeWithASingleMessage() {
        JsonNode body = json("{\"requestId\":\"a1b2c3\",\"code\":\"ORDER_REJECTED\",\"message\":\"Insufficient funds\"}");
        HighApiError error = HighApiError.fromResponse(400, body, null);
        assertEquals(400, error.status());
        assertEquals("ORDER_REJECTED", error.code());
        assertEquals("a1b2c3", error.requestId());
        assertEquals(List.of("Insufficient funds"), error.messages());
        assertTrue(error.getMessage().contains("Insufficient funds"));
    }

    @Test
    void keepsEveryMessageForAValidationErrorArray() {
        JsonNode body = json("{\"code\":\"VALIDATION_ERROR\",\"message\":[\"quantity is required\",\"price must be positive\"]}");
        HighApiError error = HighApiError.fromResponse(422, body, null);
        assertEquals(List.of("quantity is required", "price must be positive"), error.messages());
        assertTrue(error.getMessage().contains("quantity is required"));
        assertTrue(error.getMessage().contains("price must be positive"));
    }

    @Test
    void surfacesANonJsonErrorBodyWithItsRealStatusRatherThanAParseFailure() {
        HighApiError error = HighApiError.fromResponse(502, null, "<html><body>502 Bad Gateway</body></html>");
        assertEquals(502, error.status());
        assertNull(error.code());
        assertTrue(error.getMessage().contains("502"));
    }

    @Test
    void transportFailuresCarryStatusZero() {
        HighApiError error = new HighApiError("Request timed out after 50ms", 0);
        assertEquals(0, error.status());
        assertNull(error.code());
        assertNull(error.requestId());
    }

    @Test
    void codeIsAnOpenStringNotAClosedSet() {
        JsonNode body = json("{\"code\":\"SOME_FUTURE_GATEWAY_CODE\",\"message\":\"whatever\"}");
        HighApiError error = HighApiError.fromResponse(400, body, null);
        assertEquals("SOME_FUTURE_GATEWAY_CODE", error.code());
    }

    @Test
    void carriesTheParsedBodyWhenThereWasOne() {
        JsonNode body = json("{\"code\":\"ORDER_REJECTED\",\"message\":\"nope\"}");
        HighApiError error = HighApiError.fromResponse(400, body, null);
        assertEquals(body, error.body());
    }
}
