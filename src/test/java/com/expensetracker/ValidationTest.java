package com.expensetracker;

import com.expensetracker.exception.InvalidAmountException;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.util.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Week-4 server-side validation (NumberFormatException handling etc.).
 */
class ValidationTest {

    @Test
    void parseAmountAcceptsValidInput() throws Exception {
        assertEquals(new BigDecimal("250.50"), Validation.parseAmount("250.50"));
        assertEquals(new BigDecimal("1000"), Validation.parseAmount("1,000")); // commas stripped
        assertEquals(new BigDecimal("42"), Validation.parseAmount("  42  "));  // trimmed
    }

    @Test
    void parseAmountRejectsGarbage() {
        // Week 4: NumberFormatException inside -> friendly InvalidAmountException outside
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount("abc"));
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount("12.34.56"));
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount(""));
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount(null));
    }

    @Test
    void parseAmountRejectsNonPositive() {
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount("0"));
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount("-50"));
        assertThrows(InvalidAmountException.class, () -> Validation.parseAmount("10.999")); // >2 dp
    }

    @Test
    void parseDateAcceptsIsoDates() throws Exception {
        assertEquals(LocalDate.of(2026, 10, 13), Validation.parseDate("2026-10-13"));
    }

    @Test
    void parseDateRejectsBadOrFutureDates() {
        assertThrows(ValidationException.class, () -> Validation.parseDate("13-10-2026"));
        assertThrows(ValidationException.class, () -> Validation.parseDate("not-a-date"));
        assertThrows(ValidationException.class,
                () -> Validation.parseDate(LocalDate.now().plusDays(5).toString()));
    }

    @Test
    void requireMonthValidatesFormat() throws Exception {
        assertEquals("2026-10", Validation.requireMonth("2026-10"));
        assertThrows(ValidationException.class, () -> Validation.requireMonth("Oct 2026"));
        assertThrows(ValidationException.class, () -> Validation.requireMonth("2026-13"));
    }

    @Test
    void requireEmailValidatesShape() {
        assertDoesNotThrow(() -> Validation.requireEmail("student@college.edu"));
        assertThrows(ValidationException.class, () -> Validation.requireEmail("not-an-email"));
        assertThrows(ValidationException.class, () -> Validation.requireEmail(""));
    }
}
