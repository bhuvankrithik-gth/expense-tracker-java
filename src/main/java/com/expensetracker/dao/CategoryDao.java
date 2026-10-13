package com.expensetracker.dao;

import com.expensetracker.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Week 12 — JDBC CRUD for {@code categories}.
 * Per-user data isolation: every query is scoped with "WHERE user_id = ?"
 * so a user can never see or touch another user's categories.
 */
public class CategoryDao {

    public long create(Category category) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return create(c, category);
        }
    }

    /** INSERT on a caller-supplied connection (for multi-statement transactions). */
    public long create(Connection c, Category category) throws SQLException {
        String sql = "INSERT INTO categories (user_id, name, kind, is_default) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, category.getUserId());
            ps.setString(2, category.getName());
            ps.setString(3, category.getKind());
            ps.setBoolean(4, category.isDefault());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                category.setId(id);
                return id;
            }
        }
    }

    /** Seed the 7 default categories for a brand-new user (idempotent). */
    public void ensureDefaults(long userId) throws SQLException {
        for (String name : Category.DEFAULTS) {
            if (findByName(userId, name).isEmpty()) {
                create(new Category(userId, name, "both", true));
            }
        }
    }

    public List<Category> listByUser(long userId) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return listByUser(c, userId);
        }
    }

    /** SELECT on a caller-supplied connection. */
    public List<Category> listByUser(Connection c, long userId) throws SQLException {
        String sql = "SELECT id, user_id, name, kind, is_default FROM categories "
                   + "WHERE user_id = ? ORDER BY is_default DESC, name ASC";
        List<Category> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<Category> findById(long userId, long id) throws SQLException {
        String sql = "SELECT id, user_id, name, kind, is_default FROM categories "
                   + "WHERE user_id = ? AND id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Category> findByName(long userId, String name) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return findByName(c, userId, name);
        }
    }

    /** findByName on a caller-supplied connection. */
    public Optional<Category> findByName(Connection c, long userId, String name)
            throws SQLException {
        String sql = "SELECT id, user_id, name, kind, is_default FROM categories "
                   + "WHERE user_id = ? AND name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Only custom (non-default) categories can be deleted. */
    public boolean delete(long userId, long id) throws SQLException {
        try (Connection c = Db.getConnection()) {
            return delete(c, userId, id);
        }
    }

    /** DELETE on a caller-supplied connection. */
    public boolean delete(Connection c, long userId, long id) throws SQLException {
        String sql = "DELETE FROM categories WHERE user_id = ? AND id = ? AND is_default = 0";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Category map(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getLong("id"));
        c.setUserId(rs.getLong("user_id"));
        c.setName(rs.getString("name"));
        c.setKind(rs.getString("kind"));
        c.setDefault(rs.getBoolean("is_default"));
        return c;
    }
}
