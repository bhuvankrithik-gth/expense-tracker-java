package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Week 3 — concrete subclass of the abstract {@link Transaction}.
 * An expense reduces the balance, so its signed amount is negative.
 */
public class Expense extends Transaction {

    private static final long serialVersionUID = 1L;

    public Expense() {
        super();
    }

    public Expense(long userId, Long categoryId, BigDecimal amount, String currency,
                   LocalDate date, String note, String paymentMethod) {
        super(userId, categoryId, amount, currency, date, note, paymentMethod);
    }

    @Override
    public String getType() {
        return "expense";
    }

    @Override
    public BigDecimal getSignedAmount() {
        return amount == null ? BigDecimal.ZERO : amount.negate();
    }

    @Override
    public String describe() {
        return "Expense of " + currency + " " + amount
                + " on " + (categoryName != null ? categoryName : "Uncategorised")
                + " (" + date + ")";
    }
}
