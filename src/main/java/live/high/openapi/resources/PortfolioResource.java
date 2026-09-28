// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.ConvertPositionRequest;
import live.high.openapi.generated.model.ExitPositionRequest;
import live.high.openapi.generated.model.Funds;
import live.high.openapi.generated.model.Holdings;
import live.high.openapi.generated.model.PositionConvertResult;
import live.high.openapi.generated.model.PositionExitAllResult;
import live.high.openapi.generated.model.PositionExitResult;
import live.high.openapi.generated.model.Positions;

public final class PortfolioResource extends BaseResource {

    private static final TypeReference<Positions> POSITIONS = new TypeReference<>() {};
    private static final TypeReference<Holdings> HOLDINGS = new TypeReference<>() {};
    private static final TypeReference<Funds> FUNDS = new TypeReference<>() {};
    private static final TypeReference<PositionConvertResult> CONVERT_RESULT = new TypeReference<>() {};
    private static final TypeReference<PositionExitAllResult> EXIT_ALL_RESULT = new TypeReference<>() {};
    private static final TypeReference<PositionExitResult> EXIT_RESULT = new TypeReference<>() {};

    public PortfolioResource(ResolvedConfig config) {
        super(config);
    }

    /** Open intraday and carry-forward positions with their P&amp;L snapshot. */
    public Positions positions() {
        return get("/portfolio/positions", Transport.AuthKind.BEARER, POSITIONS);
    }

    public CompletableFuture<Positions> positionsAsync() {
        return getAsync("/portfolio/positions", Transport.AuthKind.BEARER, POSITIONS);
    }

    /** Demat holdings with their investment snapshot. */
    public Holdings holdings() {
        return get("/portfolio/holdings", Transport.AuthKind.BEARER, HOLDINGS);
    }

    public CompletableFuture<Holdings> holdingsAsync() {
        return getAsync("/portfolio/holdings", Transport.AuthKind.BEARER, HOLDINGS);
    }

    /** Cash, margin and charge balances. */
    public Funds funds() {
        return get("/portfolio/funds", Transport.AuthKind.BEARER, FUNDS);
    }

    public CompletableFuture<Funds> fundsAsync() {
        return getAsync("/portfolio/funds", Transport.AuthKind.BEARER, FUNDS);
    }

    /** Converts a position between product types. Never retried. */
    public PositionConvertResult convertPosition(ConvertPositionRequest body) {
        return write("PATCH", "/portfolio/positions/convert", body, CONVERT_RESULT);
    }

    public CompletableFuture<PositionConvertResult> convertPositionAsync(ConvertPositionRequest body) {
        return writeAsync("PATCH", "/portfolio/positions/convert", body, CONVERT_RESULT);
    }

    /** Squares off every open position. Never retried. */
    public PositionExitAllResult exitAllPositions() {
        return write("DELETE", "/portfolio/positions/exit/all", null, EXIT_ALL_RESULT);
    }

    public CompletableFuture<PositionExitAllResult> exitAllPositionsAsync() {
        return writeAsync("DELETE", "/portfolio/positions/exit/all", null, EXIT_ALL_RESULT);
    }

    /** Squares off one position. The API expects a body on this DELETE. Never retried. */
    public PositionExitResult exitPosition(ExitPositionRequest body) {
        return write("DELETE", "/portfolio/positions/exit", body, EXIT_RESULT);
    }

    public CompletableFuture<PositionExitResult> exitPositionAsync(ExitPositionRequest body) {
        return writeAsync("DELETE", "/portfolio/positions/exit", body, EXIT_RESULT);
    }
}
