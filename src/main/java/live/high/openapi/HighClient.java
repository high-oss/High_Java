// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import live.high.openapi.resources.AuthResource;
import live.high.openapi.resources.InstrumentsResource;
import live.high.openapi.resources.MarketResource;
import live.high.openapi.resources.OrdersResource;
import live.high.openapi.resources.PortfolioResource;
import live.high.openapi.resources.ProfileResource;
import live.high.openapi.resources.ScripsResource;

/**
 * The HIGH Open API client.
 *
 * <pre>{@code
 * HighClient high = new HighClient(HighClientOptions.builder()
 *     .environment(Environment.SANDBOX)
 *     .apiKey(apiKey)
 *     .accessToken(accessToken)
 *     .build());
 * Funds funds = high.portfolio().funds();
 * }</pre>
 */
public final class HighClient {

    private final ResolvedConfig config;
    private final AuthResource auth;
    private final MarketResource market;
    private final OrdersResource orders;
    private final PortfolioResource portfolio;
    private final ProfileResource profile;
    private final ScripsResource scrips;
    private final InstrumentsResource instruments;

    public HighClient(HighClientOptions options) {
        this.config = ResolvedConfig.resolve(options);
        this.auth = new AuthResource(config);
        this.market = new MarketResource(config);
        this.orders = new OrdersResource(config);
        this.portfolio = new PortfolioResource(config);
        this.profile = new ProfileResource(config);
        this.scrips = new ScripsResource(config);
        this.instruments = new InstrumentsResource(config);
    }

    public HighClient() {
        this(HighClientOptions.builder().build());
    }

    /** Where requests go and what they carry. Settled once, at construction. */
    public ResolvedConfig config() {
        return config;
    }

    public AuthResource auth() {
        return auth;
    }

    public MarketResource market() {
        return market;
    }

    public OrdersResource orders() {
        return orders;
    }

    public PortfolioResource portfolio() {
        return portfolio;
    }

    public ProfileResource profile() {
        return profile;
    }

    public ScripsResource scrips() {
        return scrips;
    }

    /** The instrument list (the scrip master), by category. Needs no credentials. */
    public InstrumentsResource instruments() {
        return instruments;
    }

    @Override
    public String toString() {
        // No fields printed beyond the class name — see ResolvedConfig's
        // class-level note on why credentials never reach a toString().
        return "HighClient{config=" + config + '}';
    }
}
