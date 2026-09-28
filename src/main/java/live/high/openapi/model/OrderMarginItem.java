// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One leg of the basket sent to {@code POST /orders/margin}.
 *
 * <p>Hand-written, not generated: the spec's request body for this operation
 * is a bare JSON array (<code>{"type": "array", "items": {...}}</code>) with
 * no named component schema wrapping the item, so OpenAPI Generator has
 * nothing to attach a model name to — there is no {@code OrderMarginItem}
 * (or similarly-named) schema in {@code generated/openapi.json} to point at.
 * Every field below is checked against the inline item schema at {@code
 * paths./orders/margin.post.requestBody.content.application/json.schema.items}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OrderMarginItem {

    /** B = Buy, S = Sell. */
    public enum TradeSide {
        B, S
    }

    public enum ProductType {
        INTRADAY, DELIVERY, MTF
    }

    public enum ExchangeSegment {
        NSE, BSE, NSEFO, BSEFO, NSECDSFO, BSECDSFO, MCXFO
    }

    @JsonProperty("tradingSymbol")
    private String tradingSymbol;

    @JsonProperty("exchangeSegment")
    private ExchangeSegment exchangeSegment;

    @JsonProperty("scripCode")
    private String scripCode;

    @JsonProperty("quantity")
    private final Integer quantity;

    @JsonProperty("tradeSide")
    private final TradeSide tradeSide;

    @JsonProperty("productType")
    private final ProductType productType;

    @JsonProperty("price")
    private BigDecimal price;

    /** {@code quantity}, {@code tradeSide} and {@code productType} are the spec's required fields. */
    public OrderMarginItem(Integer quantity, TradeSide tradeSide, ProductType productType) {
        this.quantity = quantity;
        this.tradeSide = tradeSide;
        this.productType = productType;
    }

    /** Trading symbol. Required unless {@code exchangeSegment} + {@code scripCode} are sent. */
    public OrderMarginItem tradingSymbol(String tradingSymbol) {
        this.tradingSymbol = tradingSymbol;
        return this;
    }

    /** Exchange segment. Required when {@code tradingSymbol} is not sent. */
    public OrderMarginItem exchangeSegment(ExchangeSegment exchangeSegment) {
        this.exchangeSegment = exchangeSegment;
        return this;
    }

    /** Broker scrip code (token). Required when {@code tradingSymbol} is not sent. */
    public OrderMarginItem scripCode(String scripCode) {
        this.scripCode = scripCode;
        return this;
    }

    /** Price in Rs to estimate at. Leave it out to use the LTP. */
    public OrderMarginItem price(BigDecimal price) {
        this.price = price;
        return this;
    }

    public String getTradingSymbol() {
        return tradingSymbol;
    }

    public ExchangeSegment getExchangeSegment() {
        return exchangeSegment;
    }

    public String getScripCode() {
        return scripCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public TradeSide getTradeSide() {
        return tradeSide;
    }

    public ProductType getProductType() {
        return productType;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
