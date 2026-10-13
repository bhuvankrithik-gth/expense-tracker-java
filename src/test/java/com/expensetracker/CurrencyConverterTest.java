package com.expensetracker;

import com.expensetracker.service.ConverterFactory;
import com.expensetracker.service.CurrencyConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Week-3 currency converter interface + implementations.
 */
class CurrencyConverterTest {

    @Test
    void inrIsIdentity() {
        CurrencyConverter inr = ConverterFactory.forCode("INR");
        assertEquals(100.0, inr.toInr(100.0), 0.0001);
        assertEquals(100.0, inr.fromInr(100.0), 0.0001);
        assertEquals("₹", inr.symbol());
    }

    @Test
    void dollarRoundTrip() {
        CurrencyConverter usd = ConverterFactory.forCode("USD");
        double inr = usd.toInr(10);          // 10 USD -> INR
        assertEquals(832.0, inr, 0.0001);
        assertEquals(10.0, usd.fromInr(inr), 0.0001); // back to USD
    }

    @Test
    void euroAndYenRates() {
        assertEquals(915.0, ConverterFactory.forCode("EUR").toInr(10), 0.0001);
        assertEquals(55.0, ConverterFactory.forCode("JPY").toInr(100), 0.0001);
        assertEquals("€", ConverterFactory.forCode("EUR").symbol());
        assertEquals("¥", ConverterFactory.forCode("JPY").symbol());
    }

    @Test
    void crossCurrencyConversionViaPivot() {
        // 83.20 INR == 1 USD; 1 EUR == 91.50 INR  =>  1 EUR ~= 1.0998 USD
        double usd = ConverterFactory.forCode("EUR").convert(1.0, ConverterFactory.forCode("USD"));
        assertEquals(91.50 / 83.20, usd, 0.0001);
    }

    @Test
    void unknownCodeFallsBackToInr() {
        assertEquals("INR", ConverterFactory.forCode("XYZ").code());
        assertEquals("INR", ConverterFactory.forCode(null).code());
    }

    @Test
    void convertBigDecimalHelper() {
        var result = ConverterFactory.convert(
                new java.math.BigDecimal("100"), "USD", "INR");
        assertEquals(new java.math.BigDecimal("8320.00"), result);
    }

    @Test
    void formatAddsSymbolAndGrouping() {
        String s = ConverterFactory.format(new java.math.BigDecimal("1250000"), "INR");
        assertTrue(s.startsWith("₹"));
        assertTrue(s.contains(","));
        assertTrue(s.endsWith(".00"));
    }

    @Test
    void allFourCodesRegistered() {
        assertEquals(4, ConverterFactory.codes().size());
        assertTrue(ConverterFactory.codes().containsAll(java.util.List.of("INR", "USD", "EUR", "JPY")));
    }
}
