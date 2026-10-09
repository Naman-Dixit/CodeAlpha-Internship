package com.papertrade.model;

import java.math.BigDecimal;

/** Immutable snapshot of a stock; a price change produces a new instance (thread-safe by design). */
public final class Stock {
    private final int id;
    private final String symbol;
    private final String companyName;
    private final BigDecimal currentPrice;
    private final String sector;

    public Stock(int id, String symbol, String companyName, BigDecimal currentPrice, String sector) {
        this.id = id;
        this.symbol = symbol;
        this.companyName = companyName;
        this.currentPrice = currentPrice;
        this.sector = sector;
    }

    public Stock withPrice(BigDecimal newPrice) {
        return new Stock(id, symbol, companyName, newPrice, sector);
    }

    public int getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public String getSector() { return sector; }
}
