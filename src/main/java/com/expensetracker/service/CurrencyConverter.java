package com.expensetracker.service;

/**
 * Week 3 — INTERFACE. Each currency implements conversion to/from INR
 * (INR is the internal pivot currency). Adding a new currency means
 * writing one new class — no existing code changes (Open/Closed).
 *
 * Rates are fixed demo rates (documented in the README); a production
 * app would fetch them from an exchange-rate API.
 */
public interface CurrencyConverter {

    /** ISO code, e.g. "USD". */
    String code();

    /** Human symbol for display, e.g. "$", "€", "¥", "₹". */
    String symbol();

    /** Convert an amount of THIS currency into INR. */
    double toInr(double amount);

    /** Convert an INR amount into THIS currency. */
    double fromInr(double inrAmount);

    /** Convert directly between any two currencies via the INR pivot. */
    default double convert(double amount, CurrencyConverter target) {
        return target.fromInr(this.toInr(amount));
    }
}
