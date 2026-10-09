package com.papertrade.dao;

import com.papertrade.model.Holding;
import com.papertrade.model.Stock;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HoldingDAO {
    private static final String SELECT =
            "SELECT h.id, h.user_id, h.quantity, h.average_buy_price, "
          + "s.id AS sid, s.symbol, s.company_name, s.current_price, s.sector "
          + "FROM holdings h JOIN stocks s ON s.id = h.stock_id WHERE h.user_id = ? ";

    public List<Holding> findByUser(Connection c, int userId) throws SQLException {
        List<Holding> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY s.symbol")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /** Locks the holding row (if any) for the duration of the current transaction. */
    public Optional<Holding> findByUserAndStockForUpdate(Connection c, int userId, int stockId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "AND s.id = ? FOR UPDATE")) {
            ps.setInt(1, userId);
            ps.setInt(2, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void insert(Connection c, int userId, int stockId, int quantity, BigDecimal avgPrice) throws SQLException {
        String sql = "INSERT INTO holdings (user_id, stock_id, quantity, average_buy_price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, stockId);
            ps.setInt(3, quantity);
            ps.setBigDecimal(4, avgPrice);
            ps.executeUpdate();
        }
    }

    public void update(Connection c, int holdingId, int quantity, BigDecimal avgPrice) throws SQLException {
        String sql = "UPDATE holdings SET quantity = ?, average_buy_price = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setBigDecimal(2, avgPrice);
            ps.setInt(3, holdingId);
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, int holdingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM holdings WHERE id = ?")) {
            ps.setInt(1, holdingId);
            ps.executeUpdate();
        }
    }

    private Holding map(ResultSet rs) throws SQLException {
        Stock stock = StockDAO.mapJoined(rs);
        return new Holding(rs.getInt("id"), rs.getInt("user_id"), stock, rs.getInt("quantity"),
                rs.getBigDecimal("average_buy_price"));
    }
}
