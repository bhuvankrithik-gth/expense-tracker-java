package com.expensetracker.model;

import java.time.LocalDateTime;

/**
 * Week 2 — models one row of {@code alerts}: a budget warning produced by
 * the background budget-watcher thread (Week 5).
 */
public class Alert {

    private long id;
    private long userId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;

    public Alert() {
    }

    public Alert(long userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
