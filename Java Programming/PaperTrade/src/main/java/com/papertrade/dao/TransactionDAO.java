package com.papertrade.dao;

import com.papertrade.model.TradeType;
import com.papertrade.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {
    public void insert(Connection c, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, stock_id, transaction_type, quantity, price, total_amount) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getUserId());
            ps.setInt(2, t.getStock().getId());
            ps.setString(3, t.getType().name());
            ps.setInt(4, t.getQuantity());
            ps.setBigDecimal(5, t.getPrice());
            ps.setBigDecimal(6, t.getTotalAmount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) t.setId(keys.getInt(1));
            }
        }
    }

    /** Oldest first. */
    public List<Transaction> findByUser(Connection c, int userId) throws SQLException {
        String sql = "SELECT t.id, t.user_id, t.transaction_type, t.quantity, t.price, t.total_amount, "
                   + "t.`timestamp` AS ts, s.id AS sid, s.symbol, s.company_name, s.current_price, s.sector "
                   + "FROM transactions t JOIN stocks s ON s.id = t.stock_id "
                   + "WHERE t.user_id = ? ORDER BY t.`timestamp`, t.id";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction(rs.getInt("id"), rs.getInt("user_id"), StockDAO.mapJoined(rs),
                            TradeType.valueOf(rs.getString("transaction_type")), rs.getInt("quantity"),
                            rs.getBigDecimal("price"), rs.getBigDecimal("total_amount"),
                            rs.getTimestamp("ts").toLocalDateTime()));
                }
            }
        }
        return list;
    }
}
