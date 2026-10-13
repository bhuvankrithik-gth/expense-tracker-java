package com.expensetracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the available CurrencyConverter implementations.
 * Callers ask for a converter by ISO code and never touch the
 * concrete classes — classic interface-based design (Week 3).
 */
public final class ConverterFactory {

    /** Week 8 — collection classes: an immutable List and a HashMap lookup. */
    private static final List<CurrencyConverter> ALL = List.of(
            new InrConverter(), new UsdConverter(), new EurConverter(), new JpyConverter());

    private static final Map<String, CurrencyConverter> BY_CODE = ALL.stream()
            .collect(Collectors.toMap(CurrencyConverter::code, Function.identity()));

    private ConverterFactory() {
    }

    /** All supported currency codes, e.g. [INR, USD, EUR, JPY]. */
    public static List<String> codes() {
        return ALL.stream().map(CurrencyConverter::code).toList();
    }

    /** Look up a converter; unknown codes fall back to INR. */
    public static CurrencyConverter forCode(String code) {
        if (code == null) {
            return BY_CODE.get("INR");
        }
        return BY_CODE.getOrDefault(code.toUpperCase(), BY_CODE.get("INR"));
    }

    /**
     * Convert an amount stored in one currency into the user's display
     * currency, rounded to 2 decimals for money maths.
     */
    public static BigDecimal convert(BigDecimal amount, String fromCode, String toCode) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCode != null && fromCode.equalsIgnoreCase(toCode)) {
            return amount;
        }
        double converted = forCode(fromCode).convert(amount.doubleValue(), forCode(toCode));
        return BigDecimal.valueOf(converted).setScale(2, RoundingMode.HALF_UP);
    }

    /** Format an amount with the currency symbol, e.g. "₹1,250.00". */
    public static String format(BigDecimal amount, String code) {
        CurrencyConverter c = forCode(code);
        BigDecimal rounded = amount == null ? BigDecimal.ZERO
                : amount.setScale(2, RoundingMode.HALF_UP);
        // Group thousands for readability: 1250000.00 -> 1,250,000.00
        String[] parts = rounded.toPlainString().split("\\.");
        StringBuilder grouped = new StringBuilder();
        String intPart = parts[0];
        boolean negative = intPart.startsWith("-");
        if (negative) {
            intPart = intPart.substring(1);
        }
        for (int i = 0; i < intPart.length(); i++) {
            if (i > 0 && (intPart.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(intPart.charAt(i));
        }
        return c.symbol() + (negative ? "-" : "") + grouped + "." + parts[1];
    }
}
