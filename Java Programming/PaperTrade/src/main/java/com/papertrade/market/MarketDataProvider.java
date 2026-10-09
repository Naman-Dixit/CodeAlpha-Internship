package com.papertrade.market;

import com.papertrade.exception.StockNotFoundException;
import com.papertrade.model.Stock;

import java.util.List;

/** Read-only view of the market; lets services be tested with a fake market. */
public interface MarketDataProvider {
    Stock getStock(String symbol) throws StockNotFoundException;

    Stock getStockById(int id) throws StockNotFoundException;

    List<Stock> getAllStocks();
}
