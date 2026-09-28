// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import live.high.openapi.ExpiryType;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.Expiry;
import live.high.openapi.generated.model.HistoricalCandles;
import live.high.openapi.generated.model.HistoricalRequest;
import live.high.openapi.generated.model.MarketDepth;
import live.high.openapi.generated.model.OptionChain;
import live.high.openapi.generated.model.OptionChainRequest;
import live.high.openapi.generated.model.QuotesRequest;
import live.high.openapi.generated.model.ScripInfo;
import live.high.openapi.generated.model.Touchline;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScripsResourceTest {

    private TestServer server;
    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    private ScripsResource scrips() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).accessToken("tok").build(), Map.of());
        return new ScripsResource(config);
    }

    @Test
    void quotesReturnsAMapKeyedByTradingSymbolNotAList() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"RELIANCE-EQ\":{\"scrip\":\"NSE@2885\",\"LTP\":1412.3,\"prevClose\":1405.8,"
            + "\"change\":6.5,\"changePer\":0.46}}}"));
        QuotesRequest body = new QuotesRequest().symbols(List.of("RELIANCE-EQ"));

        Map<String, Touchline> quotes = scrips().quotes(body);

        assertEquals(0, java.math.BigDecimal.valueOf(1412.3).compareTo(quotes.get("RELIANCE-EQ").getLTP()));
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("/v1/scrips/quotes", req.path());
        Map<String, Object> sent = JSON.readValue(req.body(), Map.class);
        assertEquals(List.of("RELIANCE-EQ"), sent.get("symbols"));
    }

    @Test
    void ohlcAddsTheOhlcvBlockToEachEntry() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"RELIANCE-EQ\":{\"scrip\":\"NSE@2885\",\"LTP\":1412.3,\"prevClose\":1405.8,\"change\":6.5,"
            + "\"changePer\":0.46,\"ohlcv\":{\"open\":1407,\"high\":1418.6,\"low\":1403.2,\"close\":1412.3,\"volume\":6843210}}}}"));
        QuotesRequest body = new QuotesRequest().symbols(List.of("RELIANCE-EQ"));

        Map<String, Touchline> ohlc = scrips().ohlc(body);

        assertEquals(0, java.math.BigDecimal.valueOf(1407).compareTo(ohlc.get("RELIANCE-EQ").getOhlcv().getOpen()));
        assertEquals("/v1/scrips/ohlc", server.requests().get(0).path());
    }

    @Test
    void depthFetchesFiveLevelsOfBidsAndAsksForAnEncodedSymbol() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"tradingSymbol\":\"M&M-EQ\",\"bids\":[],\"asks\":[],\"totalBidQuantity\":0,"
            + "\"totalAskQuantity\":0,\"totalAskPercentage\":0,\"totalBidsPercentage\":0}}"));
        MarketDepth depth = scrips().depth("M&M-EQ");
        assertEquals("M&M-EQ", depth.getTradingSymbol());
        assertEquals("/v1/scrips/M%26M-EQ/depth", server.requests().get(0).path());
    }

    @Test
    void expiriesEncodesTheTypePathParameterFromTheEnum() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200,
            "{\"requestId\":\"r\",\"data\":[{\"expiry\":\"2026-09-29\",\"type\":\"M\"}]}"));
        List<Expiry> expiries = scrips().expiries("NIFTY 50", ExpiryType.OPTIONS);
        assertEquals(1, expiries.size());
        assertEquals("/v1/scrips/NIFTY%2050/options/expiries", server.requests().get(0).path());
    }

    @Test
    void futureDataReturnsAListOfScripInfoWithAnIntegerScripCode() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "[{\"tradingSymbol\":\"NIFTY-30OCT26-FUT\",\"scripCode\":52175,\"exchange\":\"NSE\","
            + "\"segment\":\"NSEFO\",\"scripKey\":\"NSEFO@52175\"}]}"));
        List<ScripInfo> data = scrips().futureData("NIFTY");
        assertEquals(52175, data.get(0).getScripCode());
        assertEquals("/v1/scrips/NIFTY/future-data", server.requests().get(0).path());
    }

    @Test
    void historicalSendsEpochIntegersAndReturnsColumnarArrays() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"tradingSymbol\":\"RELIANCE-EQ\",\"interval\":\"1D\",\"timestamp\":[1789929000],"
            + "\"open\":[1407],\"high\":[1418.6],\"low\":[1403.2],\"close\":[1412.3],\"volume\":[6843210],\"oi\":[0]}}"));
        HistoricalRequest body = new HistoricalRequest()
            .tradingSymbol("RELIANCE-EQ")
            .interval(HistoricalRequest.IntervalEnum._1_D)
            .fromTime(1789929000)
            .toTime(1790330400);

        HistoricalCandles candles = scrips().historical(body);

        assertEquals(0, java.math.BigDecimal.valueOf(1412.3).compareTo(candles.getClose().get(0)));
        Map<String, Object> sent = JSON.readValue(server.requests().get(0).body(), Map.class);
        assertEquals(1789929000, sent.get("fromTime"));
        assertEquals(1790330400, sent.get("toTime"));
    }

    @Test
    void optionChainReturnsTheUnderlyingAndTheChainRows() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"baseStockInfo\":{\"tradingSymbol\":\"NIFTY\",\"scripCode\":26000,\"exchange\":\"NSE\","
            + "\"segment\":\"NSE\",\"scripKey\":\"NSE@26000\"},\"optionChain\":[],\"marketLot\":75,\"maxOrderLots\":36}"
            + "}"));
        OptionChainRequest body = new OptionChainRequest().tradingSymbol("NIFTY").expiry("2026-09-29");

        OptionChain chain = scrips().optionChain(body);

        assertEquals("NIFTY", chain.getBaseStockInfo().getTradingSymbol());
        assertTrue(server.requests().get(0).path().startsWith("/v1/scrips/option-chain"));
    }
}
