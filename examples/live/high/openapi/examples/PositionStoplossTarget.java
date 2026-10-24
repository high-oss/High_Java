// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import java.math.BigDecimal;
import live.high.openapi.Environment;
import live.high.openapi.HighApiError;
import live.high.openapi.HighClient;
import live.high.openapi.HighClientOptions;
import live.high.openapi.generated.model.PositionRiskOrderResult;
import live.high.openapi.generated.model.SetPositionStoplossRequest;
import live.high.openapi.generated.model.SetPositionTargetRequest;

/**
 * Places a stop-loss and a target exit order against an open intraday RELIANCE
 * position. The quantity comes from the live position, so the request does not
 * carry one. Run it with:
 *
 * <pre>
 * HIGH_ENVIRONMENT=sandbox HIGH_ACCESS_TOKEN=... \
 *   gradle runExample -PmainClass=live.high.openapi.examples.PositionStoplossTarget
 * </pre>
 *
 * <p>Before running:
 * <ul>
 *   <li>These calls need a static IP registered with HIGH. From any other
 *       address they will be refused.</li>
 *   <li>Confirm the symbol, product type and both prices against the live
 *       position before pointing this at PRODUCTION. The prices below are
 *       placeholders. A real stop-loss and target must sit on the correct
 *       side of the current LTP.</li>
 *   <li>The SDK never retries these PATCH calls. If one fails, check the
 *       position and orders before resubmitting, or you may place a second
 *       exit.</li>
 * </ul>
 */
public final class PositionStoplossTarget {

    private PositionStoplossTarget() {}

    public static void main(String[] args) {
        HighClient high = new HighClient(HighClientOptions.builder()
            .environment(Environment.SANDBOX)
            .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
            .build());

        try {
            // Stop-loss: orderId is omitted, so this places a new order.
            // Passing the orderId from a previous result would amend that order.
            PositionRiskOrderResult stoploss = high.portfolio().setStoploss(new SetPositionStoplossRequest()
                .tradingSymbol("RELIANCE-EQ")
                .flavor(SetPositionStoplossRequest.FlavorEnum.REGULAR)
                .productType(SetPositionStoplossRequest.ProductTypeEnum.INTRADAY)
                .triggerPrice(new BigDecimal("2800.00")));
            printResult("stop-loss", stoploss);

            // Target: same shape, with the target price instead of a trigger.
            PositionRiskOrderResult target = high.portfolio().setTarget(new SetPositionTargetRequest()
                .tradingSymbol("RELIANCE-EQ")
                .flavor(SetPositionTargetRequest.FlavorEnum.REGULAR)
                .productType(SetPositionTargetRequest.ProductTypeEnum.INTRADAY)
                .targetPrice(new BigDecimal("2950.00")));
            printResult("target", target);
        } catch (HighApiError error) {
            System.err.println("HIGH API error " + error.status() + " " + error.code() + ": " + error.getMessage());
        }
    }

    private static void printResult(String label, PositionRiskOrderResult result) {
        // action is PLACED for a new order, MODIFIED for an amended one.
        System.out.println(label + ": action=" + result.getAction()
            + " orderId=" + result.getOrderId()
            + " limitPrice=" + result.getLimitPrice());
    }
}
