package com.papertrade.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Holding {
    private final int id;
    private final int userId;
    private Stock stock;
    private final int quantity;
    private final BigDecimal averageBuyPrice;

    public Holding(int id, int userId, Stock stock, int quantity, BigDecimal averageBuyPrice) {
        this.id = id;
        this.userId = userId;
        this.stock = stock;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
    }

    /** New average = (oldQty*oldAvg + addQty*price) / (oldQty+addQty). */
    public static BigDecimal weightedAverage(int oldQty, BigDecimal oldAvg, int addQty, BigDecimal price) {
        BigDecimal cost = oldAvg.multiply(BigDecimal.valueOf(oldQty))
                .add(price.multiply(BigDecimal.valueOf(addQty)));
        return cost.divide(BigDecimal.valueOf((long) oldQty + addQty), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal investedValue() {
        return averageBuyPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal currentValue() {
        return stock.getCurrentPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /** (Current Price - Average Buy Price) x Quantity */
    public BigDecimal profitLoss() {
        return currentValue().subtract(investedValue());
    }

    public BigDecimal profitLossPercent() {
        BigDecimal invested = investedValue();
        if (invested.signum() == 0) return BigDecimal.ZERO;
        return profitLoss().multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAverageBuyPrice() { return averageBuyPrice; }
}
