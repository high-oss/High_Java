// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the instrument list (the scrip master) — {@code
 * live.high.openapi.resources.InstrumentsResource}'s per-row shape for its 17
 * CSV columns, in file order.
 *
 * <p>Hand-written, not generated: the CSV itself has no OpenAPI schema (only
 * its column <em>names</em> do, via {@code InstrumentsManifest.columns}), so
 * there is nothing for OpenAPI Generator to attach a model to.
 *
 * <p>A blank CSV field is {@code null} here, never {@code ""} — including on
 * the string columns, not only the numeric/date ones.
 *
 * <p><b>{@link #priceTick()} is deliberately an unscaled {@code Integer}</b>,
 * copied straight off the CSV. Its scale varies by segment (some segments
 * store paise, others a raw tick count), so there is no single "correct"
 * divisor to apply generically — guessing one would be a silent pricing bug.
 * Treat it as an opaque, segment-specific unit and do not arithmetic on it
 * without first confirming the convention for that row's {@link #segment()}.
 *
 * <p><b>{@link #strikePrice()} is a {@link BigDecimal}</b>, not a {@code
 * double}: it is a price, and the rest of this SDK's generated models already
 * represent every other price field ({@code Touchline.getLTP()}, {@code
 * Ohlcv.getOpen()}, ...) as {@code BigDecimal} for the same reason — a
 * {@code double} can misrepresent a decimal price it was never given, and a
 * strike compared or summed downstream should not carry that risk.
 *
 * <p>{@link #scripCode()} is this file's {@code scrip_code} column. It is the
 * broker-assigned numeric token (the same identity {@code
 * ScripInfo.getScripCode()} carries elsewhere in this SDK, not the {@code
 * tradingSymbol}), and it is an {@code Integer} here for the same reason it is
 * one there (contract §12: {@code scripCode} is a string on {@code Order}/
 * {@code Trade}, an integer on {@code ScripInfo} — this is the latter).
 *
 * @param exchange          e.g. {@code "NSE"}, {@code "BSE"}, {@code "MCX"}
 * @param segment           e.g. {@code "ES"} (equity), {@code "FO"}, {@code "I"}, {@code "ETF"}, {@code "SPOT"}
 * @param instrument        the row's own instrument class, e.g. {@code "FUTSTK"}, {@code "OPTIDX"},
 *                          {@code "EQUITY"} — distinct from the category used to pick which file to
 *                          download ({@code InstrumentFile.InstrumentEnum}); this is per-row, that is per-file
 * @param highTradingSymbol HIGH's own {@code tradingSymbol}, e.g. {@code "RELIANCE-EQ"}
 * @param scripKey          e.g. {@code "NSE@2885"}
 * @param isin              may be {@code null} for instruments ISIN does not cover (most derivatives)
 * @param scripCode         the broker token — see the class note
 * @param symbol            the exchange's own symbol/alias, distinct from {@code highTradingSymbol}
 * @param name              the company or contract's display name; the one column routinely containing commas
 * @param groupSeries       NSE series or BSE group, e.g. {@code "EQ"}, {@code "A"}
 * @param hasFnO            {@code 1}/{@code 0}, not a boolean — see the class note on not normalising columns
 * @param underlyingSymbol  set on derivatives only
 * @param expiry            set on derivatives only
 * @param optionType        {@code "CE"}/{@code "PE"} on options, {@code null} on everything else
 * @param strikePrice       set on options only — see the class note on why this is a {@link BigDecimal}
 * @param priceTick         unscaled — see the class note
 * @param lotSize           {@code 1} for cash-market equity
 */
public record Instrument(
    String exchange,
    String segment,
    String instrument,
    String highTradingSymbol,
    String scripKey,
    String isin,
    Integer scripCode,
    String symbol,
    String name,
    String groupSeries,
    Integer hasFnO,
    String underlyingSymbol,
    LocalDate expiry,
    String optionType,
    BigDecimal strikePrice,
    Integer priceTick,
    Integer lotSize
) {
}
