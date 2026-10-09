package com.papertrade.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HoldingTest {
    private static Holding holding(String avg, String price, int qty) {
        Stock s = new Stock(1, "AAPL", "Apple", new BigDecimal(price), "Technology");
        return new Holding(1, 1, s, qty, new BigDecimal(avg));
    }

    @Test
    void averageBuyPriceIsWeighted() {
        // 10 @ 100 then 30 @ 200  ->  (1000 + 6000) / 40 = 175
        assertEquals(0, new BigDecimal("175").compareTo(
                Holding.weightedAverage(10, new BigDecimal("100"), 30, new BigDecimal("200"))));
    }

    @Test
    void profitLossIsPriceDifferenceTimesQuantity() {
        Holding h = holding("210.00", "220.45", 10);
        assertEquals(new BigDecimal("2100.00"), h.investedValue());
        assertEquals(new BigDecimal("2204.50"), h.currentValue());
        assertEquals(new BigDecimal("104.50"), h.profitLoss());
        assertEquals(new BigDecimal("4.98"), h.profitLossPercent());
    }

    @Test
    void lossIsNegative() {
        Holding h = holding("100.00", "90.00", 5);
        assertEquals(new BigDecimal("-50.00"), h.profitLoss());
        assertEquals(new BigDecimal("-10.00"), h.profitLossPercent());
    }
}
