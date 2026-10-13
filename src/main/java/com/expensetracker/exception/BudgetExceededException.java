package com.expensetracker.exception;

import java.math.BigDecimal;

/**
 * Week 4 — CUSTOM checked exception, thrown when a new expense pushes a
 * category's monthly spend over its budget (like the textbook
 * "InsufficientFundsException" exercise). It is a *warning*, not a blocker:
 * the transaction is still saved, but the handler catches this exception
 * and shows a prominent warning banner on the page.
 */
public class BudgetExceededException extends Exception {

    private final String categoryName;
    private final BigDecimal budget;
    private final BigDecimal spent;

    public BudgetExceededException(String categoryName, BigDecimal budget, BigDecimal spent) {
        super("Budget exceeded for '" + categoryName + "': spent " + spent
                + " against a budget of " + budget);
        this.categoryName = categoryName;
        this.budget = budget;
        this.spent = spent;
    }

    public String getCategoryName() { return categoryName; }
    public BigDecimal getBudget() { return budget; }
    public BigDecimal getSpent() { return spent; }
}
