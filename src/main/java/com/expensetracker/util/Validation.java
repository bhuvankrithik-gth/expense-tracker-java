package com.expensetracker.util;

import com.expensetracker.exception.InvalidAmountException;
import com.expensetracker.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Week 4 — central server-side input validation. Every form posts here
 * before touching the DAO, so bad input becomes a friendly message on the
 * page instead of a 500 error. Demonstrates NumberFormatException handling.
 */
public final class Validation {

    private Validation() {
    }

    /**
     * Parse a money amount. Must be a valid number and strictly positive.
     * @throws InvalidAmountException with a friendly message on any problem.
     */
    public static BigDecimal parseAmount(String raw) throws InvalidAmountException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new InvalidAmountException("Please enter an amount.");
        }
        String cleaned = raw.trim().replace(",", "");
        final BigDecimal amount;
        try {
            amount = new BigDecimal(cleaned); // throws NumberFormatException on garbage
        } catch (NumberFormatException e) {
            // Week 4: catch NumberFormatException, rethrow as a friendly domain error
            throw new InvalidAmountException("'" + raw.trim() + "' is not a valid number.", e);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountException("Amount can have at most 2 decimal places.");
        }
        return amount;
    }

    /** Parse an ISO date (yyyy-MM-dd) from an HTML date input. */
    public static LocalDate parseDate(String raw) throws ValidationException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ValidationException("Please pick a date.");
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim()); // ISO format
            if (date.isAfter(LocalDate.now().plusDays(1))) {
                throw new ValidationException("Date cannot be in the future.");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new ValidationException("'" + raw.trim() + "' is not a valid date (use yyyy-MM-dd).");
        }
    }

    public static String requireText(String raw, String fieldName, int maxLen) throws ValidationException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ValidationException("Please enter " + fieldName + ".");
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new ValidationException(fieldName + " is too long (max " + maxLen + " characters).");
        }
        return value;
    }

    public static String optionalText(String raw, int maxLen) throws ValidationException {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLen) {
            throw new ValidationException("Text is too long (max " + maxLen + " characters).");
        }
        return value;
    }

    public static void requireEmail(String raw) throws ValidationException {
        String email = requireText(raw, "an email address", 190).toLowerCase();
        // Simple, readable email check — good enough for a college project.
        if (!email.matches("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}")) {
            throw new ValidationException("That doesn't look like a valid email address.");
        }
    }

    /** Validate a "YYYY-MM" month string used by budgets. */
    public static String requireMonth(String raw) throws ValidationException {
        String month = requireText(raw, "a month", 7);
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new ValidationException("Month must look like YYYY-MM (e.g. 2026-10).");
        }
        int mm = Integer.parseInt(month.substring(5));
        if (mm < 1 || mm > 12) {
            throw new ValidationException("Month must be between 01 and 12.");
        }
        return month;
    }
}
