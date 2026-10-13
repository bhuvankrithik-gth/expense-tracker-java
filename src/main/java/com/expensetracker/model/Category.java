package com.expensetracker.model;

import java.io.Serializable;

/**
 * Week 2 — models one row of {@code categories}.
 * Default categories (Food, Travel, Rent, Shopping, Bills, Education, Other)
 * are seeded at sign-up; users can add their own.
 */
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default category names created for every new user. */
    public static final String[] DEFAULTS =
            {"Food", "Travel", "Rent", "Shopping", "Bills", "Education", "Other"};

    private long id;
    private long userId;
    private String name;
    /** "expense", "income" or "both". */
    private String kind = "both";
    private boolean isDefault;

    public Category() {
    }

    public Category(long userId, String name, String kind, boolean isDefault) {
        this.userId = userId;
        this.name = name;
        this.kind = kind;
        this.isDefault = isDefault;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    @Override
    public String toString() {
        return "Category{id=" + id + ", name='" + name + "', kind='" + kind + "'}";
    }
}
