package com.papertrade.dao;

import com.papertrade.model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class OrderDAO {
    public void insert(Connection c, Order o) throws SQLException {
        String sql = "INSERT INTO orders (user_id, stock_id, order_type, quantity, price, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, o.getUserId());
            ps.setInt(2, o.getStock().getId());
            ps.setString(3, o.getType().name());
            ps.setInt(4, o.getQuantity());
            ps.setBigDecimal(5, o.getPrice());
            ps.setString(6, o.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) o.setId(keys.getInt(1));
            }
        }
    }

    public int countByUserAndStatus(Connection c, int userId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = ?")) {
            ps.setInt(1, userId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
