package com.expensetracker.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Week 2 — models one row of {@code budgets}: a monthly spending limit
 * for one category. {@code month} is stored as "YYYY-MM", e.g. "2026-10".
 */
public class Budget implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private long userId;
    private long categoryId;
    private String categoryName;   // from the JOIN, for display
    private String month;          // "YYYY-MM"
    private BigDecimal amount;
    /** Actual spend this month (filled by the DAO, not stored). */
    private BigDecimal spent = BigDecimal.ZERO;

    public Budget() {
    }

    public Budget(long userId, long categoryId, String month, BigDecimal amount) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.month = month;
        this.amount = amount;
    }

    /** Percentage of the budget already spent (0–100+, guarded against /0). */
    public double percentUsed() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0; // Week 4: ArithmeticException guarded — never divide by zero
        }
        return spent.multiply(BigDecimal.valueOf(100))
                    .divide(amount, 1, java.math.RoundingMode.HALF_UP)
                    .doubleValue();
    }

    public boolean isExceeded() {
        return amount != null && spent.compareTo(amount) > 0;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getSpent() { return spent; }
    public void setSpent(BigDecimal spent) { this.spent = spent; }
}
