package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Week 3 — concrete subclass of the abstract {@link Transaction}.
 * Income adds to the balance, so its signed amount is positive.
 */
public class Income extends Transaction {

    private static final long serialVersionUID = 1L;

    public Income() {
        super();
    }

    public Income(long userId, Long categoryId, BigDecimal amount, String currency,
                  LocalDate date, String note, String paymentMethod) {
        super(userId, categoryId, amount, currency, date, note, paymentMethod);
    }

    @Override
    public String getType() {
        return "income";
    }

    @Override
    public BigDecimal getSignedAmount() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    @Override
    public String describe() {
        return "Income of " + currency + " " + amount
                + " from " + (categoryName != null ? categoryName : "Uncategorised")
                + " (" + date + ")";
    }
}
