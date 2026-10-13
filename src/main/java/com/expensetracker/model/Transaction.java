package com.expensetracker.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Week 3 — ABSTRACT class. Every money movement is a Transaction, but the
 * sign of the amount depends on the kind, so the two abstract methods below
 * are implemented differently by {@link Expense} and {@link Income}.
 *
 * Week 7 — Serializable so deleted transactions can sit in the undo history
 * and whole-user backups can be written to disk.
 */
public abstract class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    protected long id;
    protected long userId;
    protected Long categoryId;      // nullable: category may be deleted (SET NULL)
    protected String categoryName;  // denormalised for display, from the JOIN
    protected BigDecimal amount;    // always positive; sign comes from getSignedAmount()
    protected String currency;      // currency the amount was entered in (INR/USD/EUR/JPY)
    protected LocalDate date;
    protected String note;
    protected String paymentMethod; // Cash / Card / UPI / Bank / Other
    protected LocalDateTime createdAt;

    protected Transaction() {
    }

    protected Transaction(long userId, Long categoryId, BigDecimal amount,
                          String currency, LocalDate date, String note, String paymentMethod) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.currency = currency;
        this.date = date;
        this.note = note;
        this.paymentMethod = paymentMethod;
    }

    /** Human-readable one-line summary, e.g. "Expense of ₹500.00 on Food (2026-10-13)". */
    public abstract String describe();

    /**
     * Signed amount for balance maths: negative for expenses, positive for income.
     * Implemented per subclass (Week 3 polymorphism).
     */
    public abstract BigDecimal getSignedAmount();

    /** "expense" or "income" — stored in the DB and used by the DAO. */
    public abstract String getType();

    // ---- getters / setters -------------------------------------------------
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
