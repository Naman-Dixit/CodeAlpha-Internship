package com.papertrade.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private int id;
    private final int userId;
    private final Stock stock;
    private final TradeType type;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal totalAmount;
    private final LocalDateTime timestamp;

    public Transaction(int id, int userId, Stock stock, TradeType type, int quantity,
                       BigDecimal price, BigDecimal totalAmount, LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.stock = stock;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public Stock getStock() { return stock; }
    public TradeType getType() { return type; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
