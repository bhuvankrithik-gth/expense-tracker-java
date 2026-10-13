package com.expensetracker.dao;

import com.expensetracker.model.Expense;
import com.expensetracker.model.Income;
import com.expensetracker.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Week 12 — JDBC CRUD for {@code transactions}, plus the dashboard
 * analytics queries (JOIN + GROUP BY + aggregates — see schema.sql Q1/Q2).
 * All filters are optional and bound via PreparedStatement placeholders.
 */
public class TransactionDao {

    /** INSERT a transaction; fills in the generated id. */
    public long create(Transaction t) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return create(c, t);
        }
    }

    /** INSERT using a caller-supplied connection (for multi-statement transactions). */
    public long create(Connection c, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions "
                   + "(user_id, category_id, type, amount, currency, txn_date, note, payment_method) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, t.getUserId());
            if (t.getCategoryId() == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, t.getCategoryId());
            }
            ps.setString(3, t.getType());
            ps.setBigDecimal(4, t.getAmount());
            ps.setString(5, t.getCurrency());
            ps.setDate(6, Date.valueOf(t.getDate()));
            ps.setString(7, t.getNote());
            ps.setString(8, t.getPaymentMethod());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                t.setId(id);
                return id;
            }
        }
    }

    /** SELECT one transaction, scoped to the owner (per-user isolation). */
    public Optional<Transaction> findById(long userId, long id) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return findById(c, userId, id);
        }
    }

    /** SELECT one transaction on a caller-supplied connection. */
    public Optional<Transaction> findById(Connection c, long userId, long id) throws SQLException {
        String sql = selectWithCategory() + " WHERE t.user_id = ? AND t.id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** UPDATE — only the owning user's row can change (WHERE user_id AND id). */
    public boolean update(Transaction t) throws SQLException {
        String sql = "UPDATE transactions SET category_id = ?, type = ?, amount = ?, "
                   + "currency = ?, txn_date = ?, note = ?, payment_method = ? "
                   + "WHERE user_id = ? AND id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (t.getCategoryId() == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, t.getCategoryId());
            }
            ps.setString(2, t.getType());
            ps.setBigDecimal(3, t.getAmount());
            ps.setString(4, t.getCurrency());
            ps.setDate(5, Date.valueOf(t.getDate()));
            ps.setString(6, t.getNote());
            ps.setString(7, t.getPaymentMethod());
            ps.setLong(8, t.getUserId());
            ps.setLong(9, t.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /** DELETE — scoped to the owner; returns the deleted row for the undo history. */
    public Optional<Transaction> delete(long userId, long id) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return delete(c, userId, id);
        }
    }

    /** DELETE on a caller-supplied connection. */
    public Optional<Transaction> delete(Connection c, long userId, long id) throws SQLException {
        Optional<Transaction> existing = findById(c, userId, id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        String sql = "DELETE FROM transactions WHERE user_id = ? AND id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return existing;
    }

    /** Filter criteria for the Transactions page (all optional). */
    public record Filter(
            String type,          // "expense" | "income" | null
            Long categoryId,      // null = all
            LocalDate from,       // null = no lower bound
            LocalDate to,         // null = no upper bound
            String search,        // matches note / payment method, null = none
            String sort           // "date_desc" (default), "date_asc", "amount_desc", "amount_asc"
    ) {
    }

    /**
     * SELECT with optional filters. The WHERE clause is built from fixed
     * fragments — user input only ever goes into ? placeholders.
     */
    public List<Transaction> list(long userId, Filter f, int limit) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return list(c, userId, f, limit);
        }
    }

    /** Filtered SELECT on a caller-supplied connection. */
    public List<Transaction> list(Connection c, long userId, Filter f, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(selectWithCategory()).append(" WHERE t.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (f.type() != null && (f.type().equals("expense") || f.type().equals("income"))) {
            sql.append(" AND t.type = ?");
            params.add(f.type());
        }
        if (f.categoryId() != null) {
            sql.append(" AND t.category_id = ?");
            params.add(f.categoryId());
        }
        if (f.from() != null) {
            sql.append(" AND t.txn_date >= ?");
            params.add(Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            sql.append(" AND t.txn_date <= ?");
            params.add(Date.valueOf(f.to()));
        }
        if (f.search() != null && !f.search().isBlank()) {
            sql.append(" AND (t.note LIKE ? OR t.payment_method LIKE ?)");
            params.add("%" + f.search().trim() + "%");
            params.add("%" + f.search().trim() + "%");
        }
        sql.append(switch (f.sort()) {
            case "date_asc" -> " ORDER BY t.txn_date ASC, t.id ASC";
            case "amount_desc" -> " ORDER BY t.amount DESC";
            case "amount_asc" -> " ORDER BY t.amount ASC";
            default -> " ORDER BY t.txn_date DESC, t.id DESC";
        });
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit); // limit is an int we control, not user text
        }

        List<Transaction> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /** Convenience: every transaction of a user, newest first (CSV export / backup). */
    public List<Transaction> listAll(long userId) throws SQLException {
        return list(userId, new Filter(null, null, null, null, null, "date_desc"), 0);
    }

    /** listAll on a caller-supplied connection (for multi-statement transactions). */
    public List<Transaction> listAll(Connection c, long userId) throws SQLException {
        return list(c, userId, new Filter(null, null, null, null, null, "date_desc"), 0);
    }

    // ---- dashboard analytics (JOIN + GROUP BY + aggregates) -----------------

    /** Total income / expense / count for a date range (single aggregate query). */
    public record Totals(BigDecimal income, BigDecimal expense, long count) {
    }

    public Totals totals(long userId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT "
                   + "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income, "
                   + "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense, "
                   + "COUNT(*) AS cnt "
                   + "FROM transactions WHERE user_id = ? AND txn_date BETWEEN ? AND ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Totals(rs.getBigDecimal("income"),
                        rs.getBigDecimal("expense"), rs.getLong("cnt"));
            }
        }
    }

    /**
     * Category-wise expense totals for a month.
     * SQL: JOIN transactions -> categories, GROUP BY category, SUM(amount).
     * (schema.sql sample query Q1)
     */
    public Map<String, BigDecimal> spendByCategory(long userId, String month) throws SQLException {
        String sql = "SELECT c.name AS category, SUM(t.amount) AS total "
                   + "FROM transactions t JOIN categories c ON c.id = t.category_id "
                   + "WHERE t.user_id = ? AND t.type = 'expense' "
                   + "AND DATE_FORMAT(t.txn_date, '%Y-%m') = ? "
                   + "GROUP BY c.name ORDER BY total DESC";
        Map<String, BigDecimal> out = new LinkedHashMap<>(); // keeps ORDER BY order
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("category"), rs.getBigDecimal("total"));
                }
            }
        }
        return out;
    }

    /** Monthly income vs expense for the last N months (schema.sql Q2). */
    public record MonthBucket(String month, BigDecimal income, BigDecimal expense) {
    }

    public List<MonthBucket> monthlyTrend(long userId, int months) throws SQLException {
        String sql = "SELECT DATE_FORMAT(txn_date, '%Y-%m') AS month, "
                   + "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income, "
                   + "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense "
                   + "FROM transactions WHERE user_id = ? "
                   + "AND txn_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) "
                   + "GROUP BY DATE_FORMAT(txn_date, '%Y-%m') ORDER BY month";
        List<MonthBucket> out = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, months);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MonthBucket(rs.getString("month"),
                            rs.getBigDecimal("income"), rs.getBigDecimal("expense")));
                }
            }
        }
        return out;
    }

    /** Total spend in one category for one month — used by budget checks. */
    /**
     * Total spent in a category for a month, converted to INR.
     * Transactions may be recorded in USD/EUR/JPY/INR, so each row is
     * converted with the same fixed demo rates the Java CurrencyConverter
     * implementations use (see ConverterFactory). Budgets are stored in INR,
     * so this sum is directly comparable.
     */
    public BigDecimal spendInCategoryMonthInr(long userId, long categoryId, String month)
            throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount * CASE currency "
                   + "WHEN 'USD' THEN 83.20 WHEN 'EUR' THEN 91.50 "
                   + "WHEN 'JPY' THEN 0.55 ELSE 1 END), 0) AS total "
                   + "FROM transactions "
                   + "WHERE user_id = ? AND category_id = ? AND type = 'expense' "
                   + "AND DATE_FORMAT(txn_date, '%Y-%m') = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setString(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("total");
            }
        }
    }

    // ---- row mapping ---------------------------------------------------------

    private String selectWithCategory() {
        return "SELECT t.id, t.user_id, t.category_id, t.type, t.amount, t.currency, "
             + "t.txn_date, t.note, t.payment_method, t.created_at, c.name AS category_name "
             + "FROM transactions t LEFT JOIN categories c ON c.id = t.category_id";
    }

    /** Build the right subclass from the "type" column (Week 3 polymorphism). */
    private Transaction map(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        Transaction t = "income".equals(type) ? new Income() : new Expense();
        t.setId(rs.getLong("id"));
        t.setUserId(rs.getLong("user_id"));
        long catId = rs.getLong("category_id");
        t.setCategoryId(rs.wasNull() ? null : catId);
        t.setCategoryName(rs.getString("category_name"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setCurrency(rs.getString("currency"));
        t.setDate(rs.getDate("txn_date").toLocalDate());
        t.setNote(rs.getString("note"));
        t.setPaymentMethod(rs.getString("payment_method"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            t.setCreatedAt(ts.toLocalDateTime());
        }
        return t;
    }
}
