package com.expensetracker;

import com.expensetracker.util.Passwords;
import com.expensetracker.util.Result;
import com.expensetracker.util.Svg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for password hashing, the generic Result box, and SVG chart output.
 */
class UtilTest {

    @Test
    void passwordHashVerifies() {
        String hash = Passwords.hash("secret123");
        assertTrue(Passwords.verify("secret123", hash));
        assertFalse(Passwords.verify("wrong", hash));
        assertFalse(Passwords.verify("secret123", "garbage"));
    }

    @Test
    void passwordSaltsAreUnique() {
        // Same password -> different hashes (random salt per user).
        assertNotEquals(Passwords.hash("same"), Passwords.hash("same"));
    }

    @Test
    void passwordStrengthRule() {
        assertTrue(Passwords.isStrongEnough("123456"));
        assertFalse(Passwords.isStrongEnough("12345"));
        assertFalse(Passwords.isStrongEnough(null));
    }

    @Test
    void resultBoxHoldsValueOrError() {
        Result<Integer> ok = Result.ok(42);       // autoboxing int -> Integer
        assertTrue(ok.isOk());
        assertEquals(42, ok.getValue());          // auto-unboxing back to int
        assertNull(ok.getError());

        Result<Integer> fail = Result.fail("boom");
        assertFalse(fail.isOk());
        assertEquals("boom", fail.getError());
        assertNull(fail.getValue());
    }

    @Test
    void donutProducesSvgWithSlices() {
        String svg = Svg.donut(List.of(
                new Svg.Slice("Food", 60), new Svg.Slice("Travel", 40)), 180);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Food"));
        assertTrue(svg.contains("60.0%"));
    }

    @Test
    void donutHandlesEmptyData() {
        assertTrue(Svg.donut(List.of(), 180).contains("No data"));
    }

    @Test
    void barsProduceSvgWithLabels() {
        String svg = Svg.bars(List.of("Jan", "Feb"),
                List.of(100.0, 200.0), List.of(50.0, 80.0), 400, 240);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Jan"));
        assertTrue(svg.contains("<rect"));
    }

    @Test
    void progressBarNeverDividesByZero() {
        // percentUsed() with a zero budget must not throw ArithmeticException.
        var b = new com.expensetracker.model.Budget();
        b.setAmount(java.math.BigDecimal.ZERO);
        b.setSpent(new java.math.BigDecimal("100"));
        assertEquals(0.0, b.percentUsed());
        assertTrue(Svg.progress(150).contains("width:100.0%")); // clamped
    }
}
