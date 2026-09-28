// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.ModifyOrderRequest;
import live.high.openapi.generated.model.OrderBook;
import live.high.openapi.generated.model.OrderCharges;
import live.high.openapi.generated.model.OrderChargesRequest;
import live.high.openapi.generated.model.OrderMargin;
import live.high.openapi.generated.model.OrderPlacementResult;
import live.high.openapi.generated.model.PlaceOrderRequest;
import live.high.openapi.generated.model.TradeBook;
import live.high.openapi.model.OrderMarginItem;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrdersResourceTest {

    private TestServer server;
    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    private OrdersResource orders() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).accessToken("tok").build(), Map.of());
        return new OrdersResource(config);
    }

    @Test
    void placeSendsExactlyTheDocumentedFieldsAndParsesTheResult() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"a1b2c3\",\"data\":"
            + "{\"orderId\":\"2609250000123456\",\"error\":\"\",\"isSliced\":false,\"orderIds\":[\"2609250000123456\"]}}"));

        PlaceOrderRequest body = new PlaceOrderRequest()
            .tradeSide(PlaceOrderRequest.TradeSideEnum.B)
            .tradingSymbol("RELIANCE-EQ")
            .productType(PlaceOrderRequest.ProductTypeEnum.DELIVERY)
            .flavor(PlaceOrderRequest.FlavorEnum.REGULAR)
            .orderType(PlaceOrderRequest.OrderTypeEnum.LIMIT)
            .validity(PlaceOrderRequest.ValidityEnum.DAY)
            .quantity(10)
            .price(new BigDecimal("1410"))
            .isAMO(false);

        OrderPlacementResult result = orders().place(body);

        assertEquals("2609250000123456", result.getOrderId());
        assertEquals("", result.getError());
        assertEquals(Boolean.FALSE, result.getIsSliced());
        assertEquals(List.of("2609250000123456"), result.getOrderIds());

        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("POST", req.method());
        assertEquals("/v1/orders", req.path());
        assertEquals("Bearer tok", req.headers().get("authorization"));
        Map<String, Object> sent = JSON.readValue(req.body(), Map.class);
        assertEquals("B", sent.get("tradeSide"));
        assertEquals("RELIANCE-EQ", sent.get("tradingSymbol"));
        assertEquals("DELIVERY", sent.get("productType"));
        assertEquals("REGULAR", sent.get("flavor"));
        assertEquals("LIMIT", sent.get("orderType"));
        assertEquals("DAY", sent.get("validity"));
        assertEquals(10, sent.get("quantity"));
        assertEquals(1410, ((Number) sent.get("price")).intValue());
        assertEquals(false, sent.get("isAMO"));
    }

    @Test
    void modifySendsPatchWithTheOrderId() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"orderId\":\"1\",\"error\":\"\"}}"));
        ModifyOrderRequest body = new ModifyOrderRequest()
            .orderId("2609250000123502")
            .tradeSide(ModifyOrderRequest.TradeSideEnum.B)
            .productType(ModifyOrderRequest.ProductTypeEnum.DELIVERY)
            .flavor(ModifyOrderRequest.FlavorEnum.REGULAR)
            .orderType(ModifyOrderRequest.OrderTypeEnum.LIMIT)
            .validity(ModifyOrderRequest.ValidityEnum.DAY)
            .quantity(5)
            .isAMO(false);

        orders().modify(body);

        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("PATCH", req.method());
        assertEquals("/v1/orders", req.path());
        Map<String, Object> sent = JSON.readValue(req.body(), Map.class);
        assertEquals("2609250000123502", sent.get("orderId"));
    }

    @Test
    void getFetchesOneOrderByIdAsAnOrderBook() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"orders\":[],\"scrips\":{}}}"));
        OrderBook book = orders().get("2609250000123456");
        assertEquals(List.of(), book.getOrders());
        assertEquals("GET", server.requests().get(0).method());
        assertEquals("/v1/orders/2609250000123456", server.requests().get(0).path());
    }

    @Test
    void cancelSendsDeleteWithNoBody() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"orderId\":\"2609250000123502\",\"error\":\"\"}}"));
        orders().cancel("2609250000123502");
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("DELETE", req.method());
        assertEquals("/v1/orders/2609250000123502", req.path());
        assertEquals("", req.body());
    }

    @Test
    void listFetchesTheOrderBook() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"orders\":[],\"scrips\":{}}}"));
        orders().list();
        assertEquals("/v1/orders/list", server.requests().get(0).path());
    }

    @Test
    void tradesFetchesTheTradeBook() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"trades\":[],\"scrips\":{}}}"));
        TradeBook book = orders().trades();
        assertEquals(List.of(), book.getTrades());
        assertEquals("/v1/orders/trades", server.requests().get(0).path());
    }

    @Test
    void tradesForFetchesTheFillsOfOneOrder() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"trades\":[],\"scrips\":{}}}"));
        orders().tradesFor("2609250000123456");
        assertEquals("/v1/orders/2609250000123456/trades", server.requests().get(0).path());
    }

    @Test
    void chargesSendsTheOrderShapeAndParsesTheChargesBlock() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"quantity\":10,\"price\":1410,\"productType\":\"DELIVERY\",\"tradeSide\":\"B\","
            + "\"tradedValue\":14100,\"charges\":{\"brokerage\":20}}}"));
        OrderChargesRequest body = new OrderChargesRequest()
            .tradingSymbol("RELIANCE-EQ")
            .quantity(10)
            .tradeSide(OrderChargesRequest.TradeSideEnum.B)
            .productType(OrderChargesRequest.ProductTypeEnum.DELIVERY)
            .price(new BigDecimal("1410"));

        OrderCharges charges = orders().charges(body);

        assertEquals(0, new BigDecimal("14100").compareTo(charges.getTradedValue()));
        assertEquals("/v1/orders/charges", server.requests().get(0).path());
        assertEquals("POST", server.requests().get(0).method());
    }

    @Test
    void marginSendsABareJsonArrayAndParsesTheMarginsAndSpanSummary() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"margins\":[{\"tradingSymbol\":\"RELIANCE-EQ\",\"scripKey\":\"NSE@2885\",\"quantity\":10,"
            + "\"tradeSide\":\"B\",\"initialMargin\":14100,\"exposureMargin\":0}],"
            + "\"spanSummary\":{\"span\":0,\"exposureMargin\":0,\"optionPremium\":0,\"marginBenefit\":0,\"totalMargin\":14100}}}"));

        List<OrderMarginItem> body = List.of(
            new OrderMarginItem(10, OrderMarginItem.TradeSide.B, OrderMarginItem.ProductType.DELIVERY)
                .tradingSymbol("RELIANCE-EQ"));

        OrderMargin margin = orders().margin(body);

        assertEquals(1, margin.getMargins().size());
        assertEquals("RELIANCE-EQ", margin.getMargins().get(0).getTradingSymbol());

        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("/v1/orders/margin", req.path());
        // The request body itself must be a bare JSON array, not an object.
        assertTrue(req.body().trim().startsWith("["));
        List<Map<String, Object>> sent = JSON.readValue(req.body(), List.class);
        assertEquals(1, sent.size());
        assertEquals("RELIANCE-EQ", sent.get(0).get("tradingSymbol"));
        assertEquals("B", sent.get(0).get("tradeSide"));
        assertEquals("DELIVERY", sent.get(0).get("productType"));
        assertNull(sent.get(0).get("exchangeSegment"));
    }
}
