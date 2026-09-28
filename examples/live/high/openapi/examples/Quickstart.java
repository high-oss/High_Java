// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.examples;

import live.high.openapi.Environment;
import live.high.openapi.HighApiError;
import live.high.openapi.HighClient;
import live.high.openapi.HighClientOptions;
import live.high.openapi.LogLevel;
import live.high.openapi.generated.model.Funds;

/**
 * Fetches account funds. Run it with:
 *
 * <pre>
 * HIGH_ENVIRONMENT=sandbox HIGH_API_KEY=... HIGH_ACCESS_TOKEN=... \
 *   gradle runExample -PmainClass=live.high.openapi.examples.Quickstart
 * </pre>
 *
 * or, without Gradle, against the built jar:
 *
 * <pre>
 * java -cp build/classes/java/examples:build/classes/java/main:&lt;jackson jars&gt; \
 *   live.high.openapi.examples.Quickstart
 * </pre>
 */
public final class Quickstart {

    private Quickstart() {}

    public static void main(String[] args) {
        HighClient high = new HighClient(HighClientOptions.builder()
            .environment(Environment.SANDBOX)
            .apiKey(System.getenv("HIGH_API_KEY"))
            .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
            .logLevel(LogLevel.INFO)
            .build());

        try {
            Funds funds = high.portfolio().funds();
            System.out.println("Available balance: " + funds.getAvailableBalance());
        } catch (HighApiError error) {
            System.err.println("HIGH API error " + error.status() + " " + error.code() + ": " + error.getMessage());
        }
    }
}
