package com.expensetracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Week 12 — batch INSERTs for {@code activity_log}. The producer-consumer
 * log service (Week 5) collects entries in memory and flushes them here
 * in one batch, so logging never slows down request handling.
 */
public class ActivityLogDao {

    /** One log entry: which user did what. */
    public record Entry(Long userId, String action, String detail) {
    }

    public void insertBatch(List<Entry> entries) throws SQLException {
        if (entries.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO activity_log (user_id, action, detail) VALUES (?, ?, ?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Entry e : entries) {
                if (e.userId() == null) {
                    ps.setNull(1, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(1, e.userId());
                }
                ps.setString(2, e.action());
                ps.setString(3, e.detail());
                ps.addBatch();
            }
            ps.executeBatch(); // one round-trip for the whole batch
        }
    }
}
