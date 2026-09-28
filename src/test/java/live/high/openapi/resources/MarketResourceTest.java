// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.MarketStatus;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MarketResourceTest {

    private TestServer server;

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    @Test
    void statusSendsTheBearerTokenAndParsesExchangeStatusAndIsMock() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"isHoliday\":false,\"date\":\"2026-09-28\",\"exchangeStatus\":"
            + "{\"NSE\":{\"status\":\"OPEN\",\"timeRemainingInSeconds\":12600,\"isMock\":null}}}}"));
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).accessToken("tok").build(), java.util.Map.of());
        MarketResource market = new MarketResource(config);

        MarketStatus status = market.status();

        assertEquals(Boolean.FALSE, status.getIsHoliday());
        assertEquals("OPEN", status.getExchangeStatus().get("NSE").getStatus());
        assertEquals("Bearer tok", server.requests().get(0).headers().get("authorization"));
        assertTrue(server.requests().get(0).path().startsWith("/v1/market/status"));
    }
}
