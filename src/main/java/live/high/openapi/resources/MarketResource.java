// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.MarketStatus;

public final class MarketResource extends BaseResource {

    private static final TypeReference<MarketStatus> MARKET_STATUS = new TypeReference<>() {};

    public MarketResource(ResolvedConfig config) {
        super(config);
    }

    /** Session state per exchange for today. */
    public MarketStatus status() {
        return get("/market/status", Transport.AuthKind.BEARER, MARKET_STATUS);
    }

    public CompletableFuture<MarketStatus> statusAsync() {
        return getAsync("/market/status", Transport.AuthKind.BEARER, MARKET_STATUS);
    }
}
