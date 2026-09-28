// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.ConvertPositionRequest;
import live.high.openapi.generated.model.ExitPositionRequest;
import live.high.openapi.generated.model.Funds;
import live.high.openapi.generated.model.Holdings;
import live.high.openapi.generated.model.PositionConvertResult;
import live.high.openapi.generated.model.PositionExitAllResult;
import live.high.openapi.generated.model.PositionExitResult;
import live.high.openapi.generated.model.Positions;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PortfolioResourceTest {

    private TestServer server;
    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    private PortfolioResource portfolio() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).accessToken("tok").build(), Map.of());
        return new PortfolioResource(config);
    }

    @Test
    void positionsHasASnapshotDifferentFromHoldings() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"totalStocks\":1,\"snapshot\":{\"totalPL\":100},\"positions\":[],\"scrips\":{}}}"));
        Positions positions = portfolio().positions();
        assertEquals("/v1/portfolio/positions", server.requests().get(0).path());
        assertEquals(0, java.math.BigDecimal.valueOf(100).compareTo(positions.getSnapshot().getTotalPL()));
    }

    @Test
    void holdingsHasASnapshotShapedDifferentlyFromPositions() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"totalStocks\":1,\"snapshot\":{\"investment\":1000},\"holdings\":[],\"scrips\":{}}}"));
        Holdings holdings = portfolio().holdings();
        assertEquals("/v1/portfolio/holdings", server.requests().get(0).path());
        assertEquals(0, java.math.BigDecimal.valueOf(1000).compareTo(holdings.getSnapshot().getInvestment()));
    }

    @Test
    void fundsFetchesTheBalances() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"availableBalance\":50000,\"ledgerBalance\":50000,\"todaysBalance\":0,\"marginUtilized\":0,"
            + "\"marginAgainstAssets\":0,\"todaysPayIn\":0,\"todaysPayout\":0,\"unsettledFutureAmount\":0}}"));
        Funds funds = portfolio().funds();
        assertEquals("/v1/portfolio/funds", server.requests().get(0).path());
        assertEquals(0, java.math.BigDecimal.valueOf(50000).compareTo(funds.getAvailableBalance()));
    }

    @Test
    void convertPositionSendsPatchWithSourceAndTargetProductType() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"tradingSymbol\":\"RELIANCE-EQ\",\"exchangeSegment\":\"NSE\",\"scripCode\":\"2885\","
            + "\"quantity\":10,\"isSubmitted\":true}}"));
        ConvertPositionRequest body = new ConvertPositionRequest()
            .tradingSymbol("RELIANCE-EQ")
            .tradeSide(ConvertPositionRequest.TradeSideEnum.B)
            .quantity(10)
            .sourceProductType(ConvertPositionRequest.SourceProductTypeEnum.INTRADAY)
            .targetProductType(ConvertPositionRequest.TargetProductTypeEnum.DELIVERY);

        PositionConvertResult result = portfolio().convertPosition(body);

        assertEquals(Boolean.TRUE, result.getIsSubmitted());
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("PATCH", req.method());
        assertEquals("/v1/portfolio/positions/convert", req.path());
        Map<String, Object> sent = JSON.readValue(req.body(), Map.class);
        assertEquals("INTRADAY", sent.get("sourceProductType"));
        assertEquals("DELIVERY", sent.get("targetProductType"));
    }

    @Test
    void exitAllPositionsSendsDeleteWithNoBody() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"totalCount\":0,\"successCount\":0,\"failureCount\":0,\"positions\":[]}}"));
        PositionExitAllResult result = portfolio().exitAllPositions();
        assertEquals(0, result.getTotalCount());
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("DELETE", req.method());
        assertEquals("/v1/portfolio/positions/exit/all", req.path());
        assertEquals("", req.body());
    }

    @Test
    void exitPositionSendsDeleteWithABody() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"tradingSymbol\":\"RELIANCE-EQ\",\"exchangeSegment\":\"NSE\",\"scripCode\":\"2885\","
            + "\"quantity\":10,\"flavor\":\"REGULAR\",\"isSubmitted\":true}}"));
        ExitPositionRequest body = new ExitPositionRequest()
            .tradingSymbol("RELIANCE-EQ")
            .tradeSide(ExitPositionRequest.TradeSideEnum.S)
            .productType(ExitPositionRequest.ProductTypeEnum.INTRADAY)
            .quantity(10)
            .flavor(ExitPositionRequest.FlavorEnum.REGULAR);

        PositionExitResult result = portfolio().exitPosition(body);

        assertEquals(Boolean.TRUE, result.getIsSubmitted());
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("DELETE", req.method());
        assertEquals("/v1/portfolio/positions/exit", req.path());
        Map<String, Object> sent = JSON.readValue(req.body(), Map.class);
        assertEquals("RELIANCE-EQ", sent.get("tradingSymbol"));
        assertEquals("S", sent.get("tradeSide"));
    }
}
