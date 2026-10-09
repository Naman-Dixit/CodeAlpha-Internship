package com.papertrade.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Base class for buy/sell orders; subclasses define the direction of the cash flow. */
public abstract class Order {
    private int id;
    private final int userId;
    private final Stock stock; // HAS-A
    private final int quantity;
    private final BigDecimal price;
    private OrderStatus status = OrderStatus.EXECUTED;
    private final LocalDateTime createdAt = LocalDateTime.now();

    protected Order(int userId, Stock stock, int quantity) {
        this.userId = userId;
        this.stock = stock;
        this.quantity = quantity;
        this.price = stock.getCurrentPrice();
    }

    public abstract TradeType getType();

    /** Signed effect on the cash balance: negative for BUY, positive for SELL. */
    public abstract BigDecimal cashImpact();

    public BigDecimal totalAmount() {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public Stock getStock() { return stock; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
