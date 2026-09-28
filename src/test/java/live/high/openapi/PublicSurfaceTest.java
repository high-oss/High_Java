// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import live.high.openapi.resources.AuthResource;
import live.high.openapi.resources.MarketResource;
import live.high.openapi.resources.OrdersResource;
import live.high.openapi.resources.PortfolioResource;
import live.high.openapi.resources.ScripsResource;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    private static HighClient client() {
        return new HighClient(HighClientOptions.builder().accessToken("tok").apiKey("key").build());
    }

    @Test
    void exposesAllFiveResourcesOnAConstructedClient() {
        HighClient client = client();
        assertTrue(client.auth() != null);
        assertTrue(client.market() != null);
        assertTrue(client.orders() != null);
        assertTrue(client.portfolio() != null);
        assertTrue(client.scrips() != null);
    }

    /**
     * Every operation the SDK covers as a method, sync form only (the {@code
     * ...Async} forms mirror these 1:1). The spec has 27 operations; the SDK
     * covers 24 — login, the two consent operations and token introspection
     * are deliberately excluded (contract §1).
     */
    @Test
    void exposesEveryCoveredOperationAsAMethod() {
        Map<Class<?>, List<String>> expected = Map.of(
            AuthResource.class, List.of("generateAccessToken"),
            OrdersResource.class, List.of(
                "place", "modify", "get", "cancel", "list", "trades", "tradesFor", "charges", "margin"),
            PortfolioResource.class, List.of(
                "positions", "holdings", "funds", "convertPosition", "exitAllPositions", "exitPosition"),
            ScripsResource.class, List.of(
                "quotes", "ohlc", "depth", "expiries", "futureData", "historical", "optionChain"),
            MarketResource.class, List.of("status"));

        int count = 0;
        for (Map.Entry<Class<?>, List<String>> entry : expected.entrySet()) {
            List<String> methodNames = Arrays.stream(entry.getKey().getMethods())
                .map(Method::getName)
                .toList();
            for (String name : entry.getValue()) {
                assertTrue(methodNames.contains(name), entry.getKey().getSimpleName() + "." + name);
                count++;
            }
        }
        assertEquals(24, count);
    }

    @Test
    void wrapsNeitherTheBrowserLoginPageNorTheConsentFlowNorIntrospection() {
        for (String name : new String[] {
            "login", "loginUrl", "renderLogin", "generateConsent", "consumeConsent", "validateToken"
        }) {
            boolean exists = Arrays.stream(AuthResource.class.getMethods())
                .anyMatch(m -> m.getName().equals(name));
            assertFalse(exists, "AuthResource." + name + " must not exist");
        }
    }

    // --- credential containment ------------------------------------------

    @Test
    void doesNotSerialiseCredentialsThroughToString() {
        HighClient client = new HighClient(
            HighClientOptions.builder().accessToken("SECRET-TOKEN").apiKey("SECRET-KEY").build());
        String printed = client.toString();
        assertFalse(printed.contains("SECRET-TOKEN"));
        assertFalse(printed.contains("SECRET-KEY"));
        assertFalse(client.config().toString().contains("SECRET-TOKEN"));
        assertFalse(client.config().toString().contains("SECRET-KEY"));
    }

    @Test
    void doesNotSerialiseCredentialsThroughJacksonEither() throws Exception {
        HighClient client = new HighClient(
            HighClientOptions.builder().accessToken("SECRET-TOKEN").apiKey("SECRET-KEY").build());
        // Fluent-style accessors (orders(), not getOrders()) mean Jackson finds
        // no bean properties at all here and would otherwise refuse to
        // serialise an "empty bean" — disabling that failure is what lets this
        // assert on the actual (credential-free) output rather than on an
        // exception that happens to also prove the point.
        ObjectMapper mapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);
        String json = mapper.writeValueAsString(client);
        assertFalse(json.contains("SECRET-TOKEN"));
        assertFalse(json.contains("SECRET-KEY"));

        for (Object resource : new Object[] {
            client.auth(), client.orders(), client.portfolio(), client.scrips(), client.market()
        }) {
            String resourceJson = mapper.writeValueAsString(resource);
            assertFalse(resourceJson.contains("SECRET-TOKEN"));
            assertFalse(resourceJson.contains("SECRET-KEY"));
        }
    }

    @Test
    void keepsCredentialsUsableDespiteBeingHiddenFromToString() {
        HighClient client = new HighClient(
            HighClientOptions.builder().accessToken("SECRET-TOKEN").build());
        assertEquals("SECRET-TOKEN", client.config().accessToken().orElseThrow());
    }
}
