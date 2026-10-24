// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ExpiryType;
import live.high.openapi.Paths;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.Expiry;
import live.high.openapi.generated.model.HistoricalCandles;
import live.high.openapi.generated.model.HistoricalRequest;
import live.high.openapi.generated.model.IntradayRequest;
import live.high.openapi.generated.model.MarketDepth;
import live.high.openapi.generated.model.OptionChain;
import live.high.openapi.generated.model.OptionChainRequest;
import live.high.openapi.generated.model.QuotesRequest;
import live.high.openapi.generated.model.ScripInfo;
import live.high.openapi.generated.model.Touchline;

public final class ScripsResource extends BaseResource {

    private static final TypeReference<Map<String, Touchline>> QUOTE_MAP = new TypeReference<>() {};
    private static final TypeReference<MarketDepth> MARKET_DEPTH = new TypeReference<>() {};
    private static final TypeReference<List<Expiry>> EXPIRY_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ScripInfo>> SCRIP_INFO_LIST = new TypeReference<>() {};
    private static final TypeReference<HistoricalCandles> HISTORICAL_CANDLES = new TypeReference<>() {};
    private static final TypeReference<OptionChain> OPTION_CHAIN = new TypeReference<>() {};

    public ScripsResource(ResolvedConfig config) {
        super(config);
    }

    /**
     * Last traded price and session change, keyed by trading symbol.
     *
     * <p>Returns a <b>map</b>, not a list (contract §12) — {@code QuoteMap} in
     * the spec is {@code additionalProperties: {$ref: Touchline}}, a pure map
     * schema the generator inlines as {@code Map<String, Touchline>} rather
     * than naming; there is no generated {@code QuoteMap} class to reference.
     */
    public Map<String, Touchline> quotes(QuotesRequest body) {
        return write("POST", "/scrips/quotes", body, QUOTE_MAP);
    }

    public CompletableFuture<Map<String, Touchline>> quotesAsync(QuotesRequest body) {
        return writeAsync("POST", "/scrips/quotes", body, QUOTE_MAP);
    }

    /**
     * Quotes plus the session OHLCV block, keyed by trading symbol.
     *
     * <p>Takes {@link QuotesRequest}, not a separate "OhlcRequest": the spec
     * inlines an identical {@code symbols}/{@code scripKeys} request body on
     * both {@code /scrips/quotes} and {@code /scrips/ohlc}, and the generator
     * deduplicates identical inline schemas into the one model — there is no
     * generated {@code OhlcRequest} to use instead.
     */
    public Map<String, Touchline> ohlc(QuotesRequest body) {
        return write("POST", "/scrips/ohlc", body, QUOTE_MAP);
    }

    public CompletableFuture<Map<String, Touchline>> ohlcAsync(QuotesRequest body) {
        return writeAsync("POST", "/scrips/ohlc", body, QUOTE_MAP);
    }

    /** Five levels of bids and asks. */
    public MarketDepth depth(String symbol) {
        return get(Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", symbol)),
            Transport.AuthKind.BEARER, MARKET_DEPTH);
    }

    public CompletableFuture<MarketDepth> depthAsync(String symbol) {
        return getAsync(Paths.pathOf("/scrips/{symbol}/depth", Map.of("symbol", symbol)),
            Transport.AuthKind.BEARER, MARKET_DEPTH);
    }

    /** Available expiries for a derivative underlying. */
    public List<Expiry> expiries(String symbol, ExpiryType type) {
        return get(Paths.pathOf("/scrips/{symbol}/{type}/expiries",
                Map.of("symbol", symbol, "type", type.wireValue())),
            Transport.AuthKind.BEARER, EXPIRY_LIST);
    }

    public CompletableFuture<List<Expiry>> expiriesAsync(String symbol, ExpiryType type) {
        return getAsync(Paths.pathOf("/scrips/{symbol}/{type}/expiries",
                Map.of("symbol", symbol, "type", type.wireValue())),
            Transport.AuthKind.BEARER, EXPIRY_LIST);
    }

    /** The futures contracts on an underlying. */
    public List<ScripInfo> futureData(String symbol) {
        return get(Paths.pathOf("/scrips/{symbol}/future-data", Map.of("symbol", symbol)),
            Transport.AuthKind.BEARER, SCRIP_INFO_LIST);
    }

    public CompletableFuture<List<ScripInfo>> futureDataAsync(String symbol) {
        return getAsync(Paths.pathOf("/scrips/{symbol}/future-data", Map.of("symbol", symbol)),
            Transport.AuthKind.BEARER, SCRIP_INFO_LIST);
    }

    /**
     * Historical candles, returned columnar: every array has the same length
     * and index i across them describes one candle (contract §12). The
     * instrument is named by {@code tradingSymbol}, or by {@code
     * exchangeSegment} + {@code scripCode} when no trading symbol is set.
     */
    public HistoricalCandles historical(HistoricalRequest body) {
        return write("POST", "/scrips/historical", body, HISTORICAL_CANDLES);
    }

    public CompletableFuture<HistoricalCandles> historicalAsync(HistoricalRequest body) {
        return writeAsync("POST", "/scrips/historical", body, HISTORICAL_CANDLES);
    }

    /**
     * Candles of the most recent trading session at a fixed interval, in the
     * same columnar shape as {@link #historical}. The instrument is named the
     * same way: {@code tradingSymbol}, or {@code exchangeSegment} +
     * {@code scripCode}.
     */
    public HistoricalCandles intraday(IntradayRequest body) {
        return write("POST", "/scrips/intraday", body, HISTORICAL_CANDLES);
    }

    public CompletableFuture<HistoricalCandles> intradayAsync(IntradayRequest body) {
        return writeAsync("POST", "/scrips/intraday", body, HISTORICAL_CANDLES);
    }

    /** The option chain for one underlying and expiry. */
    public OptionChain optionChain(OptionChainRequest body) {
        return write("POST", "/scrips/option-chain", body, OPTION_CHAIN);
    }

    public CompletableFuture<OptionChain> optionChainAsync(OptionChainRequest body) {
        return writeAsync("POST", "/scrips/option-chain", body, OPTION_CHAIN);
    }
}
