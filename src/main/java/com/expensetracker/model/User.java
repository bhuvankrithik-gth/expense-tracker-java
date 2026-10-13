package com.expensetracker.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Week 2 — plain Java object (POJO) modelling one row of the {@code users} table.
 * Encapsulation: fields are private, accessed through getters/setters.
 * Implements Serializable so a full user backup can be written to disk (Week 7).
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private String email;
    /** PBKDF2 hash in "iterations:salt:hash" form — never the plain password. */
    private String passwordHash;
    /** Display currency chosen in Settings: INR, USD, EUR or JPY. */
    private String currency = "INR";
    private String theme = "light";
    private LocalDateTime createdAt;

    /** No-arg constructor required when the DAO builds objects row by row. */
    public User() {
    }

    /** Convenience constructor used at sign-up time. */
    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
    }
}
