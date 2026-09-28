// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.Paths;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.ModifyOrderRequest;
import live.high.openapi.generated.model.OrderBook;
import live.high.openapi.generated.model.OrderCancelResult;
import live.high.openapi.generated.model.OrderCharges;
import live.high.openapi.generated.model.OrderChargesRequest;
import live.high.openapi.generated.model.OrderMargin;
import live.high.openapi.generated.model.OrderPlacementResult;
import live.high.openapi.generated.model.PlaceOrderRequest;
import live.high.openapi.generated.model.TradeBook;
import live.high.openapi.model.OrderMarginItem;

public final class OrdersResource extends BaseResource {

    private static final TypeReference<OrderPlacementResult> PLACEMENT_RESULT = new TypeReference<>() {};
    private static final TypeReference<OrderBook> ORDER_BOOK = new TypeReference<>() {};
    private static final TypeReference<OrderCancelResult> CANCEL_RESULT = new TypeReference<>() {};
    private static final TypeReference<TradeBook> TRADE_BOOK = new TypeReference<>() {};
    private static final TypeReference<OrderCharges> ORDER_CHARGES = new TypeReference<>() {};
    private static final TypeReference<OrderMargin> ORDER_MARGIN = new TypeReference<>() {};

    public OrdersResource(ResolvedConfig config) {
        super(config);
    }

    /** Places a regular, bracket, cover or GTT order. Never retried. */
    public OrderPlacementResult place(PlaceOrderRequest body) {
        return write("POST", "/orders", body, PLACEMENT_RESULT);
    }

    public CompletableFuture<OrderPlacementResult> placeAsync(PlaceOrderRequest body) {
        return writeAsync("POST", "/orders", body, PLACEMENT_RESULT);
    }

    /** Modifies a pending order. Never retried. */
    public OrderPlacementResult modify(ModifyOrderRequest body) {
        return write("PATCH", "/orders", body, PLACEMENT_RESULT);
    }

    public CompletableFuture<OrderPlacementResult> modifyAsync(ModifyOrderRequest body) {
        return writeAsync("PATCH", "/orders", body, PLACEMENT_RESULT);
    }

    /** One order, in the same shape as the order book. */
    public OrderBook get(String orderId) {
        return get(Paths.pathOf("/orders/{orderId}", java.util.Map.of("orderId", orderId)),
            Transport.AuthKind.BEARER, ORDER_BOOK);
    }

    public CompletableFuture<OrderBook> getAsync(String orderId) {
        return getAsync(Paths.pathOf("/orders/{orderId}", java.util.Map.of("orderId", orderId)),
            Transport.AuthKind.BEARER, ORDER_BOOK);
    }

    /** Cancels a pending order. Never retried. */
    public OrderCancelResult cancel(String orderId) {
        return write("DELETE", Paths.pathOf("/orders/{orderId}", java.util.Map.of("orderId", orderId)), null, CANCEL_RESULT);
    }

    public CompletableFuture<OrderCancelResult> cancelAsync(String orderId) {
        return writeAsync("DELETE", Paths.pathOf("/orders/{orderId}", java.util.Map.of("orderId", orderId)), null, CANCEL_RESULT);
    }

    /** Today's order book. */
    public OrderBook list() {
        return get("/orders/list", Transport.AuthKind.BEARER, ORDER_BOOK);
    }

    public CompletableFuture<OrderBook> listAsync() {
        return getAsync("/orders/list", Transport.AuthKind.BEARER, ORDER_BOOK);
    }

    /** Today's trade book. */
    public TradeBook trades() {
        return get("/orders/trades", Transport.AuthKind.BEARER, TRADE_BOOK);
    }

    public CompletableFuture<TradeBook> tradesAsync() {
        return getAsync("/orders/trades", Transport.AuthKind.BEARER, TRADE_BOOK);
    }

    /** The fills of one order. */
    public TradeBook tradesFor(String orderId) {
        return get(Paths.pathOf("/orders/{orderId}/trades", java.util.Map.of("orderId", orderId)),
            Transport.AuthKind.BEARER, TRADE_BOOK);
    }

    public CompletableFuture<TradeBook> tradesForAsync(String orderId) {
        return getAsync(Paths.pathOf("/orders/{orderId}/trades", java.util.Map.of("orderId", orderId)),
            Transport.AuthKind.BEARER, TRADE_BOOK);
    }

    /** Estimated brokerage and statutory charges for one order. */
    public OrderCharges charges(OrderChargesRequest body) {
        return write("POST", "/orders/charges", body, ORDER_CHARGES);
    }

    public CompletableFuture<OrderCharges> chargesAsync(OrderChargesRequest body) {
        return writeAsync("POST", "/orders/charges", body, ORDER_CHARGES);
    }

    /**
     * Margin required for a basket of orders. The request body is a bare JSON
     * array per the spec (no named component schema), hence {@link
     * OrderMarginItem} rather than a generated type — see its Javadoc.
     */
    public OrderMargin margin(List<OrderMarginItem> body) {
        return write("POST", "/orders/margin", body, ORDER_MARGIN);
    }

    public CompletableFuture<OrderMargin> marginAsync(List<OrderMarginItem> body) {
        return writeAsync("POST", "/orders/margin", body, ORDER_MARGIN);
    }
}
