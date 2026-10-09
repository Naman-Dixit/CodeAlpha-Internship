package com.papertrade.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A user's cash plus a collection of holdings (composition). */
public class Portfolio {
    private final BigDecimal cash;
    private final List<Holding> holdings;

    public Portfolio(BigDecimal cash, List<Holding> holdings) {
        this.cash = cash;
        this.holdings = new ArrayList<>(holdings);
    }

    public BigDecimal getCash() { return cash; }
    public List<Holding> getHoldings() { return Collections.unmodifiableList(holdings); }

    public BigDecimal investedValue() {
        return holdings.stream().map(Holding::investedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal stockValue() {
        return holdings.stream().map(Holding::currentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Portfolio Value = Cash Balance + Current Market Value of Holdings */
    public BigDecimal totalValue() {
        return cash.add(stockValue());
    }

    /** Unrealised P/L = current market value of holdings - invested capital. */
    public BigDecimal unrealizedProfitLoss() {
        return stockValue().subtract(investedValue());
    }

    /** Return % = P/L / invested capital x 100 (0 when nothing is invested). */
    public BigDecimal returnPercent() {
        BigDecimal invested = investedValue();
        if (invested.signum() == 0) return BigDecimal.ZERO;
        return unrealizedProfitLoss().multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP);
    }

    /** Overall account P/L versus the starting virtual capital (includes realised gains). */
    public BigDecimal accountProfitLoss() {
        return totalValue().subtract(User.INITIAL_BALANCE);
    }

    public BigDecimal accountReturnPercent() {
        return accountProfitLoss().multiply(BigDecimal.valueOf(100))
                .divide(User.INITIAL_BALANCE, 2, RoundingMode.HALF_UP);
    }
}
