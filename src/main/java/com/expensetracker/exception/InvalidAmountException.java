package com.expensetracker.exception;

/**
 * Week 4 — custom exception for amounts that fail server-side validation
 * (not a number, zero or negative). Handlers catch it and re-render the
 * form with a friendly message instead of a stack trace.
 */
public class InvalidAmountException extends Exception {

    public InvalidAmountException(String message) {
        super(message);
    }

    public InvalidAmountException(String message, Throwable cause) {
        super(message, cause);
    }
}
