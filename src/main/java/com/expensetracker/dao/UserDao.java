package com.expensetracker.dao;

import com.expensetracker.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Week 12 — JDBC CRUD for {@code users}. Every SQL statement uses
 * PreparedStatement with ? placeholders: user input is never concatenated
 * into SQL (prevents SQL injection).
 */
public class UserDao {

    /** INSERT a new user; fills in the generated id. */
    public long create(User user) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, currency, theme) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getCurrency());
            ps.setString(5, user.getTheme());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                user.setId(id);
                return id;
            }
        }
    }

    /** SELECT by email — used at login (email has a UNIQUE index). */
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, password_hash, currency, theme, created_at "
                   + "FROM users WHERE email = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** SELECT by primary key. */
    public Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT id, name, email, password_hash, currency, theme, created_at "
                   + "FROM users WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** UPDATE display currency / theme from the Settings page. */
    public void updatePreferences(long userId, String currency, String theme) throws SQLException {
        String sql = "UPDATE users SET currency = ?, theme = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setString(2, theme);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    /** Map one ResultSet row to a User object (DAO mapping pattern). */
    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setName(rs.getString("name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setCurrency(rs.getString("currency"));
        u.setTheme(rs.getString("theme"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            u.setCreatedAt(ts.toLocalDateTime());
        }
        return u;
    }
}
