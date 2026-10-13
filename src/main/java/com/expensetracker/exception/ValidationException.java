package com.expensetracker.exception;

/**
 * Week 4 — general server-side validation failure (blank name, bad date,
 * unknown category, ...). Carries a user-friendly message shown on the page.
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }
}
