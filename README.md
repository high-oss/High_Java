# high-openapi

Official Java SDK for the [HIGH Open API](https://openapi.high.live). Typed
against the canonical OpenAPI contract, built on `java.net.http.HttpClient`,
with Jackson as its one runtime dependency.

## Install

Maven:

```xml
<dependency>
  <groupId>live.high</groupId>
  <artifactId>high-openapi</artifactId>
  <version>0.0.1</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("live.high:high-openapi:0.0.1")
```

JDK 17 or newer.

## Quickstart

```java
import live.high.openapi.*;
import live.high.openapi.generated.model.Funds;

HighClient high = new HighClient(HighClientOptions.builder()
    .environment(Environment.SANDBOX)        // or PRODUCTION (the default)
    .apiKey(System.getenv("HIGH_API_KEY"))
    .accessToken(System.getenv("HIGH_ACCESS_TOKEN"))
    .build());

Funds funds = high.portfolio().funds();
System.out.println(funds.getAvailableBalance());
```

See [`examples/`](./examples) for a runnable version of this, plus one for
each resource.

## Configuration

Built via `HighClientOptions.builder()`:

| Option | Default | Notes |
|---|---|---|
| `environment(Environment)` | `PRODUCTION` | `PRODUCTION` or `SANDBOX` |
| `baseUrl(String)` | from `environment` | Overrides the REST host |
| `wsBaseUrl(String)` | from `environment` | Datafeed socket host, reserved for the feed client |
| `versionPath(String)` | `"v1"` | The segment between host and operation path |
| `apiKey(String)` | `HIGH_API_KEY` | Sent as `x-api-key`, for `auth().generateAccessToken(...)` |
| `accessToken(String)` | `HIGH_ACCESS_TOKEN` | Sent as `Authorization: Bearer` |
| `timeoutMs(int)` | `30000` | Per request, covering the whole exchange |
| `maxRetries(int)` | `2` | Idempotent reads only |
| `maxRetryDelayMs(int)` | `30000` | Ceiling on one retry delay; a longer `Retry-After` fails fast |
| `logLevel(LogLevel)` | `SILENT` | `SILENT`, `ERROR`, `WARN`, `INFO`, `DEBUG` |
| `logSink(LogSink)` | console | Where log lines go |
| `userAgent(String)` | — | Appended to the SDK's own |
| `httpClient(HttpClient)` | a default client | Transport override |

Host resolution order: explicit `baseUrl`, then explicit `environment`, then
`HIGH_BASE_URL`, then `HIGH_ENVIRONMENT`, then production. An unknown
environment throws at construction, naming the valid values — never at
request time.

**The trap:** an explicit `environment` suppresses `HIGH_BASE_URL` and
`HIGH_WS_BASE_URL` entirely. Without this, a `HIGH_BASE_URL` left in a shell
profile from a mock-server session would silently route a client built with
`.environment(SANDBOX)` at production, and could leave the REST host on one
environment and the socket host on the other.

Sandbox and production issue **separate API keys**. Switching environment
means switching credentials too; the SDK does not rewrite them for you.

## Authentication

The SDK covers the **TOTP flow**, which is one call:

```java
HighClient high = new HighClient(HighClientOptions.builder()
    .apiKey(System.getenv("HIGH_API_KEY")).build());

AccessToken token = high.auth().generateAccessToken("C1", "123456");

// Use it for everything else.
HighClient trading = new HighClient(HighClientOptions.builder()
    .accessToken(token.getAccessToken()).build());
```

A HIGH access token lasts 24 hours. The SDK attaches whatever you give it and
**does not refresh** — minting the next one needs a fresh TOTP, so the timing
is yours to choose. Configuration is settled at construction, so a new token
means a new client.

### What this SDK does not do

It wraps the TOTP endpoint and nothing else from the auth area. The redirect
consent flow (`/auth/login`, `/auth/generate-consent`, `/auth/consume-consent`)
and token introspection (`/auth/validate-token`) are not part of the SDK —
the first three are built around a browser page only a human can complete,
and the last is unnecessary: a caller learns a token is invalid from the next
call's 401. If you need them, call those endpoints directly.

## Errors

Every failure is a `HighApiError` (an unchecked `RuntimeException` —
see [Development](#development) for why), including timeouts and transport
failures, which carry `status: 0`. No raw `HttpClient` exception and no
Jackson parse exception escapes the SDK.

```java
import live.high.openapi.HighApiError;

try {
    high.orders().place(order);
} catch (HighApiError error) {
    error.status();     // HTTP status, or 0 for transport failures
    error.code();        // e.g. "ORDER_REJECTED" — see ErrorCodes
    error.requestId();   // quote this in support tickets
    error.messages();    // validation errors return several
    error.body();        // the parsed payload (a Jackson JsonNode), for a non-standard error
}
```

`ErrorCodes.ALL` holds the documented catalogue. The gateway can return codes
outside it, so `code()` is a plain `String` — compare against the constants,
but do not assume the set is closed.

## Retries

Retried: `GET` and `HEAD` only, on 429, 500, 502, 503 and 504, honouring
`Retry-After` in both its seconds and HTTP-date forms, with exponential
backoff otherwise. A `Retry-After` longer than `maxRetryDelayMs` (30s by
default) is not waited out — the SDK gives up and throws, rather than
blocking your call for as long as an edge WAF or a misconfigured rate limiter
asks.

Never retried: `POST`, `PATCH` and `DELETE`. A retried `orders().place(...)`
would be a duplicate order, and a retried square-off would be a second
square-off.

## Logging

Off by default. Each level prints itself and everything more severe.

```java
HighClient high = new HighClient(HighClientOptions.builder()
    .accessToken(accessToken).logLevel(LogLevel.DEBUG).build());
// 2026-09-28T17:05:12.345Z DEBUG HIGH -> POST https://openapi.high.live/v1/orders {body={tradingSymbol=RELIANCE-EQ, quantity=1, ...}}
// 2026-09-28T17:05:12.488Z DEBUG HIGH <- 200 in 143ms https://openapi.high.live/v1/orders {requestId=a1b2c3, body={orderId=..., error=}}
```

| Level | What it prints |
|---|---|
| `ERROR` | API error responses (status, code, requestId), timeouts, transport failures |
| `WARN` | Each retry, and giving up when `Retry-After` exceeds the ceiling |
| `INFO` | One line per request: method and URL |
| `DEBUG` | The above plus request and response bodies, and response timing |

Every line is prefixed with an ISO-8601 timestamp and the level.

Credentials never reach the log. Headers are not logged at all, and the
`tOtp`, `apiKey`, `accessToken`, `tokenId` and `stepToken` query parameters
are replaced with `REDACTED` in logged URLs; the same keys (plus `otp`,
`password`, `pin`, `authorization`, `refreshToken`) are masked at any nesting
depth in logged bodies — so debug logs are safe to ship to an aggregator.
Pass `.logSink(...)` to route lines somewhere other than the console.

## Cancellation

`java.net.http` has no `AbortSignal` or `Context` equivalent, so cancellation
goes through Java's own idiom: every operation has a synchronous form and an
`...Async` form returning `CompletableFuture<T>`. Cancelling that future —
`future.cancel(true)` — reaches whichever stage is active, the in-flight HTTP
call or a pending retry delay, and is surfaced as a plain
`CancellationException` from `.join()`: never converted into a timeout, never
retried afterward.

```java
CompletableFuture<Map<String, Touchline>> future =
    high.scrips().quotesAsync(new QuotesRequest().symbols(List.of("RELIANCE-EQ")));
scheduler.schedule(() -> future.cancel(true), 1, TimeUnit.SECONDS);
```

The synchronous form (`high.scrips().quotes(...)`) has no cancellation
primitive of its own — there is nothing in `java.util.concurrent` that
composes with a blocking call the way `AbortSignal` does in JavaScript or
`context.Context` does in Go. A caller who needs cancellation uses the async
form; `.join()`/`.get()` on the result is not it.

## Resources

24 operations across five resources, each reachable as a method on
`HighClient`.

| Resource | Methods |
|---|---|
| `auth()` | `generateAccessToken` |
| `orders()` | `place` · `modify` · `get` · `cancel` · `list` · `trades` · `tradesFor` · `charges` · `margin` |
| `portfolio()` | `positions` · `holdings` · `funds` · `convertPosition` · `exitAllPositions` · `exitPosition` |
| `scrips()` | `quotes` · `ohlc` · `depth` · `expiries` · `futureData` · `historical` · `optionChain` |
| `market()` | `status` |

Each method returns the response's `data` — the `{requestId, data}` envelope
is unwrapped for you, and `requestId` reaches you on the error. Request and
response types are the generated models in `live.high.openapi.generated.model`
(see [Regenerating](#regenerating-from-the-spec)).

A few shapes worth knowing, because they are not what you might guess:

```java
// quotes and ohlc return a MAP keyed by trading symbol, not a list
Map<String, Touchline> quotes = high.scrips().quotes(new QuotesRequest().symbols(List.of("RELIANCE-EQ")));
quotes.get("RELIANCE-EQ").getLTP();

// historical returns COLUMNAR arrays — index i across them is one candle
HistoricalCandles c = high.scrips().historical(new HistoricalRequest()
    .tradingSymbol("RELIANCE-EQ")
    .interval(HistoricalRequest.IntervalEnum._1_D)
    .fromTime(1789929000)     // epoch seconds
    .toTime(1790330400));
c.getClose().get(0);          // not c.get(0).getClose()

// positions and holdings both have a snapshot, with DIFFERENT shapes
high.portfolio().positions().getSnapshot().getTotalPL();
high.portfolio().holdings().getSnapshot().getInvestment();

// scripCode is a STRING on Order/Trade, an INTEGER on ScripInfo
high.orders().get(orderId).getOrders().get(0).getScripCode();   // String
high.scrips().futureData("NIFTY").get(0).getScripCode();        // Integer
```

`orders().margin(...)` and `scrips().expiries(...)` are the two operations
whose request shape the spec never names: see the Javadoc on
`live.high.openapi.model.OrderMarginItem` and `live.high.openapi.ExpiryType`
for why, and `scripts/regenerate.sh`'s header comment for the one schema
(`Error`'s `message` field) the generator cannot produce compilable Java for
at all.

## Instrument list

The instrument list (the scrip master): every scrip HIGH knows, as CSV rows,
one category at a time.

```java
import live.high.openapi.generated.model.InstrumentFile;
import live.high.openapi.model.Instrument;
import java.util.stream.Stream;

// Streaming is the primary form — always consume it in try-with-resources.
try (Stream<Instrument> equities = high.instruments().stream(InstrumentFile.InstrumentEnum.EQUITY)) {
    equities.filter(i -> "NSE".equals(i.exchange())).forEach(i -> System.out.println(i.highTradingSymbol()));
}

// Or collect it eagerly, for a smaller category or when you want a List.
List<Instrument> etfs = high.instruments().list(InstrumentFile.InstrumentEnum.ETFS);
```

Five categories: `ALL`, `EQUITY`, `DERIVATIVES`, `COMMODITY`, `ETFS`. `ALL` and
`DERIVATIVES` are large (tens of megabytes) — `stream(...)` parses lazily off
the response body rather than buffering the file, so prefer it over `list(...)`
for those two.

Each row is an `Instrument` with 17 typed fields: `exchange`, `segment`,
`instrument`, `highTradingSymbol`, `scripKey`, `isin`, `scripCode`, `symbol`,
`name`, `groupSeries`, `hasFnO`, `underlyingSymbol`, `expiry`, `optionType`,
`strikePrice`, `priceTick`, `lotSize`. A blank field is `null`, never `""`.
`priceTick` is deliberately left as the raw integer the file carries — its
scale varies by segment, and rescaling it generically would be a silent
pricing bug.

This needs **no credentials** — call it before you have an `accessToken`, or
on a client built with none at all. The list is rebuilt once each trading
morning and does not change intraday, so **cache the rows for the day** in
your own application rather than re-fetching per call.

## Live datafeed

No socket client ships in this release. `wsBaseUrl` is resolved and exposed
on `ResolvedConfig`, but nothing consumes it yet.

## Regenerating from the spec

Models come from [OpenAPI Generator](https://openapi-generator.tech)
(`java`, `native` library, Jackson serialization) run against the spec at the
commit pinned in `spec.lock.json`, with one recorded exception — see
`scripts/regenerate.sh`'s header comment for exactly which two schemas are
hand-written instead and why.

```bash
./scripts/regenerate.sh   # rewrites generated/ from the pinned commit
```

`generated/` is committed and never hand-edited. To move to a newer
contract, update `spec.lock.json`, regenerate, and commit both.

## Development

No JDK or Gradle is required on the host; everything runs in Docker:

```bash
docker run --rm -v "$(pwd):/w" -v gradle-cache:/home/gradle/.gradle -w /w \
  gradle:8-jdk17 bash -lc "gradle test --no-daemon"
```

Tests run against a real local `com.sun.net.httpserver.HttpServer` rather
than a mocked `HttpClient`, so a change in how requests are built is caught
rather than asserted around.

A few Java-specific decisions, recorded so they are not re-litigated:

- **`HighApiError` is unchecked.** A checked exception on every resource
  method would force a `throws` clause through the builder-style facade and
  through every lambda a caller wraps a call in (a retry loop, a stream
  pipeline, a `CompletableFuture` chain) — and `HttpClient`'s own async path
  is already unchecked, so unchecked is the idiom the rest of the surface has
  to compose with anyway.
- **`ResolvedConfig` is a hand-written final class, not a `record`.** A
  record's generated `toString()` prints every component, which is exactly
  the trap for a type carrying an API key and a bearer token — see its
  Javadoc.
- **Enums, not widened strings**, wherever the spec fixes a set: `Environment`,
  `LogLevel`, `ExpiryType`, and every enum field on a generated request/response
  model. `live.high.openapi.ExpiryType` is hand-written specifically because
  the spec declares that one inline rather than as a shared component, and a
  test (`ExpiryTypeTest`) pins it to the pinned spec so drift breaks the
  build instead of silently widening to `String`.

## Licence

MIT. See [LICENSE](./LICENSE).
