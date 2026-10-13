package com.expensetracker.dao;

import com.expensetracker.model.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Week 12 — JDBC CRUD for {@code alerts} (budget warnings).
 */
public class AlertDao {

    public void create(Alert alert) throws SQLException {
        String sql = "INSERT INTO alerts (user_id, message) VALUES (?, ?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, alert.getUserId());
            ps.setString(2, alert.getMessage());
            ps.executeUpdate();
        }
    }

    public List<Alert> unreadByUser(long userId) throws SQLException {
        String sql = "SELECT id, user_id, message, is_read, created_at FROM alerts "
                   + "WHERE user_id = ? AND is_read = 0 ORDER BY created_at DESC LIMIT 20";
        List<Alert> out = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public void markAllRead(long userId) throws SQLException {
        String sql = "UPDATE alerts SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /** Dedup: don't spam the same budget warning twice while it's unread. */
    public boolean hasUnreadLike(long userId, String messagePrefix) throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts "
                   + "WHERE user_id = ? AND is_read = 0 AND message LIKE ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, messagePrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private Alert map(ResultSet rs) throws SQLException {
        Alert a = new Alert();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        a.setMessage(rs.getString("message"));
        a.setRead(rs.getBoolean("is_read"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            a.setCreatedAt(ts.toLocalDateTime());
        }
        return a;
    }
}
