package com.papertrade.model;

import java.math.BigDecimal;

public class SellOrder extends Order {
    public SellOrder(int userId, Stock stock, int quantity) { super(userId, stock, quantity); }

    @Override public TradeType getType() { return TradeType.SELL; }
    @Override public BigDecimal cashImpact() { return totalAmount(); }
}
