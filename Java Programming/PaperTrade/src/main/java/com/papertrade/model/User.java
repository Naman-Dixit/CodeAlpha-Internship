package com.papertrade.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class User {
    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000.00");

    private final int id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private BigDecimal balance;
    private final LocalDateTime createdAt;
    private Portfolio portfolio; // HAS-A

    public User(int id, String name, String email, String passwordHash, BigDecimal balance, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
}
