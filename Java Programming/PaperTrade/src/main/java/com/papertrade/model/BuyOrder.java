package com.papertrade.model;

import java.math.BigDecimal;

public class BuyOrder extends Order {
    public BuyOrder(int userId, Stock stock, int quantity) { super(userId, stock, quantity); }

    @Override public TradeType getType() { return TradeType.BUY; }
    @Override public BigDecimal cashImpact() { return totalAmount().negate(); }
}
