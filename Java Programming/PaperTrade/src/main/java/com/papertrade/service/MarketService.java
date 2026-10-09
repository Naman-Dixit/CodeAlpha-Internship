package com.papertrade.service;

import com.papertrade.dao.StockDAO;
import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.StockNotFoundException;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.market.MarketDataProvider;
import com.papertrade.model.Stock;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory source of truth for current simulated prices. Stock objects are immutable and are
 * replaced atomically in a ConcurrentHashMap, so the simulator thread and the UI thread never race.
 */
public class MarketService implements MarketDataProvider {
    private final Map<String, Stock> bySymbol = new ConcurrentHashMap<>();

    public MarketService(Collection<Stock> stocks) {
        for (Stock s : stocks) bySymbol.put(s.getSymbol(), s);
    }

    public static MarketService loadFromDatabase(StockDAO dao) {
        try (Connection c = DatabaseConnection.getConnection()) {
            List<Stock> stocks = dao.findAll(c);
            if (stocks.isEmpty()) {
                throw new DatabaseException("No stocks found. Run database/seed.sql first.", null);
            }
            return new MarketService(stocks);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load market data. Did you run database/schema.sql "
                    + "and database/seed.sql?", e);
        }
    }

    @Override
    public Stock getStock(String symbol) throws StockNotFoundException {
        Stock s = bySymbol.get(symbol == null ? "" : symbol.trim().toUpperCase());
        if (s == null) throw new StockNotFoundException(symbol);
        return s;
    }

    @Override
    public Stock getStockById(int id) throws StockNotFoundException {
        return bySymbol.values().stream().filter(s -> s.getId() == id).findFirst()
                .orElseThrow(() -> new StockNotFoundException("#" + id));
    }

    @Override
    public List<Stock> getAllStocks() {
        return bySymbol.values().stream().sorted(Comparator.comparingInt(Stock::getId)).toList();
    }

    /** Atomically replaces the stock with a copy carrying the new price. */
    public void updatePrice(String symbol, BigDecimal newPrice) {
        bySymbol.computeIfPresent(symbol, (k, old) -> old.withPrice(newPrice));
    }
}
