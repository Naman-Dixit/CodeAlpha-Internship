package com.papertrade.dao;

import com.papertrade.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {
    private static final String COLUMNS = "SELECT id, name, email, password_hash, balance, created_at FROM users ";

    public int insert(Connection c, User u) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, balance) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getName());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPasswordHash());
            ps.setBigDecimal(4, u.getBalance());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated key returned for user");
                return keys.getInt(1);
            }
        }
    }

    public Optional<User> findByEmail(Connection c, String email) throws SQLException {
        return findOne(c, COLUMNS + "WHERE email = ?", email);
    }

    public Optional<User> findById(Connection c, int id) throws SQLException {
        return findOne(c, COLUMNS + "WHERE id = ?", id);
    }

    /** Locks the row until the surrounding transaction ends (prevents concurrent double-spending). */
    public Optional<User> findByIdForUpdate(Connection c, int id) throws SQLException {
        return findOne(c, COLUMNS + "WHERE id = ? FOR UPDATE", id);
    }

    public void updateBalance(Connection c, int id, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public List<Integer> findAllIds(Connection c) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt(1));
        }
        return ids;
    }

    public boolean deleteByEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE email = ?")) {
            ps.setString(1, email);
            return ps.executeUpdate() > 0;
        }
    }

    private Optional<User> findOne(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getBigDecimal("balance"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }
    }
}
