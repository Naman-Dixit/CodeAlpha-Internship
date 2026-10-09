package com.papertrade.dao;

import com.papertrade.model.PortfolioSnapshot;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PortfolioHistoryDAO {
    public void insert(Connection c, int userId, BigDecimal stockValue, BigDecimal cash, BigDecimal total)
            throws SQLException {
        String sql = "INSERT INTO portfolio_history (user_id, portfolio_value, cash_balance, total_value) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setBigDecimal(2, stockValue);
            ps.setBigDecimal(3, cash);
            ps.setBigDecimal(4, total);
            ps.executeUpdate();
        }
    }

    /** Most recent {@code limit} snapshots, returned oldest first. */
    public List<PortfolioSnapshot> findRecent(Connection c, int userId, int limit) throws SQLException {
        String sql = "SELECT `timestamp` AS ts, cash_balance, portfolio_value, total_value "
                   + "FROM portfolio_history WHERE user_id = ? ORDER BY `timestamp` DESC, id DESC LIMIT ?";
        List<PortfolioSnapshot> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PortfolioSnapshot(rs.getTimestamp("ts").toLocalDateTime(),
                            rs.getBigDecimal("cash_balance"), rs.getBigDecimal("portfolio_value"),
                            rs.getBigDecimal("total_value")));
                }
            }
        }
        Collections.reverse(list);
        return list;
    }
}
