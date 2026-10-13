package com.expensetracker.dao;

import com.expensetracker.model.Budget;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Week 12 — JDBC CRUD for {@code budgets}. The list query JOINs budgets to
 * categories and embeds a correlated subquery that SUMs the month's spend
 * (schema.sql Q3). One rule keeps mixed currencies sane: budgets.amount is
 * ALWAYS stored in INR, and the spend subquery converts every transaction to
 * INR with the same fixed demo rates the Java converters use.
 */
public class BudgetDao {

    /** INSERT or UPDATE a monthly budget (one row per user/category/month). */
    public void upsert(Budget b) throws SQLException {
        try (Connection c = Db.getConnection()) {
            upsert(c, b);
        }
    }

    /** Upsert on a caller-supplied connection (for multi-statement transactions). */
    public void upsert(Connection c, Budget b) throws SQLException {
        String sql = "INSERT INTO budgets (user_id, category_id, month, amount) "
                   + "VALUES (?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, b.getUserId());
            ps.setLong(2, b.getCategoryId());
            ps.setString(3, b.getMonth());
            ps.setBigDecimal(4, b.getAmount());
            ps.executeUpdate();
        }
    }

    /**
     * Budgets for a month, each with category name + actual spend attached.
     * The spend is converted to INR (budgets are stored in INR) using the
     * fixed demo rates from ConverterFactory (USD 83.20, EUR 91.50, JPY 0.55).
     */
    public List<Budget> listByMonth(long userId, String month) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return listByMonth(c, userId, month);
        }
    }

    /** Budgets for a month on a caller-supplied connection. */
    public List<Budget> listByMonth(Connection c, long userId, String month) throws SQLException {
        String sql = "SELECT b.id, b.user_id, b.category_id, b.month, b.amount, "
                   + "c.name AS category_name, "
                   + "COALESCE((SELECT SUM(t.amount * CASE t.currency "
                   + "  WHEN 'USD' THEN 83.20 WHEN 'EUR' THEN 91.50 "
                   + "  WHEN 'JPY' THEN 0.55 ELSE 1 END) FROM transactions t "
                   + "  WHERE t.user_id = b.user_id AND t.category_id = b.category_id "
                   + "  AND t.type = 'expense' "
                   + "  AND DATE_FORMAT(t.txn_date, '%Y-%m') = b.month), 0) AS spent "
                   + "FROM budgets b JOIN categories c ON c.id = b.category_id "
                   + "WHERE b.user_id = ? AND b.month = ? "
                   + "ORDER BY c.name";
        List<Budget> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Budget b = new Budget();
                    b.setId(rs.getLong("id"));
                    b.setUserId(rs.getLong("user_id"));
                    b.setCategoryId(rs.getLong("category_id"));
                    b.setMonth(rs.getString("month"));
                    b.setAmount(rs.getBigDecimal("amount"));
                    b.setCategoryName(rs.getString("category_name"));
                    b.setSpent(rs.getBigDecimal("spent"));
                    out.add(b);
                }
            }
        }
        return out;
    }

    public boolean delete(long userId, long id) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return delete(c, userId, id);
        }
    }

    /** DELETE on a caller-supplied connection. */
    public boolean delete(Connection c, long userId, long id) throws SQLException {
        String sql = "DELETE FROM budgets WHERE user_id = ? AND id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** All user ids that have any budget — used by the background watcher. */
    public List<Long> usersWithBudgets() throws SQLException {
        String sql = "SELECT DISTINCT user_id FROM budgets";
        List<Long> out = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getLong(1));
            }
        }
        return out;
    }

    /** Distinct months a user has budgets for (newest first). */
    public List<String> monthsForUser(long userId) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return monthsForUser(c, userId);
        }
    }

    /** monthsForUser on a caller-supplied connection. */
    public List<String> monthsForUser(Connection c, long userId) throws SQLException {
        String sql = "SELECT DISTINCT month FROM budgets WHERE user_id = ? ORDER BY month DESC";
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** Amount of a specific budget (for the exceeded-warning message). */
    public BigDecimal amountFor(long userId, long categoryId, String month) throws SQLException {
        String sql = "SELECT amount FROM budgets WHERE user_id = ? AND category_id = ? AND month = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setString(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }
}
