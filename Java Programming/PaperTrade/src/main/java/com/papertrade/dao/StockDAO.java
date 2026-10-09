package com.papertrade.dao;

import com.papertrade.model.Stock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StockDAO {
    public List<Stock> findAll(Connection c) throws SQLException {
        String sql = "SELECT id AS sid, symbol, company_name, current_price, sector FROM stocks ORDER BY id";
        List<Stock> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapJoined(rs));
        }
        return list;
    }

    public void updatePrices(Connection c, Collection<Stock> stocks) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE stocks SET current_price = ? WHERE id = ?")) {
            for (Stock s : stocks) {
                ps.setBigDecimal(1, s.getCurrentPrice());
                ps.setInt(2, s.getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Maps columns sid, symbol, company_name, current_price, sector (shared by joined queries). */
    static Stock mapJoined(ResultSet rs) throws SQLException {
        return new Stock(rs.getInt("sid"), rs.getString("symbol"), rs.getString("company_name"),
                rs.getBigDecimal("current_price"), rs.getString("sector"));
    }
}
