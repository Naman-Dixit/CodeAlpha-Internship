package com.papertrade.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioTest {
    private static Holding h(int id, String sym, String avg, String price, int qty) {
        return new Holding(id, 1, new Stock(id, sym, sym, new BigDecimal(price), "Tech"), qty, new BigDecimal(avg));
    }

    @Test
    void emptyPortfolioHandlesZeroInvestmentSafely() {
        Portfolio p = new Portfolio(User.INITIAL_BALANCE, List.of());
        assertEquals(new BigDecimal("1000000.00"), p.totalValue());
        assertEquals(0, p.returnPercent().signum());
        assertEquals(0, p.accountProfitLoss().signum());
    }

    @Test
    void totalValueIsCashPlusMarketValue() {
        Portfolio p = new Portfolio(new BigDecimal("620000.00"),
                List.of(h(1, "AAPL", "210.00", "220.00", 10), h(2, "NVDA", "170.00", "180.00", 5)));
        assertEquals(new BigDecimal("3100.00"), p.stockValue());
        assertEquals(new BigDecimal("623100.00"), p.totalValue());
    }

    @Test
    void profitLossAndReturnPercent() {
        // invested 2100 + 850 = 2950 ; value 2200 + 900 = 3100 ; P/L 150 ; return 5.08%
        Portfolio p = new Portfolio(new BigDecimal("100.00"),
                List.of(h(1, "AAPL", "210.00", "220.00", 10), h(2, "NVDA", "170.00", "180.00", 5)));
        assertEquals(new BigDecimal("2950.00"), p.investedValue());
        assertEquals(new BigDecimal("150.00"), p.unrealizedProfitLoss());
        assertEquals(new BigDecimal("5.08"), p.returnPercent());
    }

    @Test
    void accountProfitLossIsRelativeToStartingCapital() {
        Portfolio p = new Portfolio(new BigDecimal("1010000.00"), List.of());
        assertEquals(new BigDecimal("10000.00"), p.accountProfitLoss());
        assertEquals(new BigDecimal("1.00"), p.accountReturnPercent());
    }

    @Test
    void holdingsListIsReadOnly() {
        Portfolio p = new Portfolio(BigDecimal.ZERO, List.of());
        assertThrows(UnsupportedOperationException.class, () -> p.getHoldings().add(h(1, "A", "1", "1", 1)));
    }

    @Test
    void buyAndSellOrdersHaveOppositeCashImpact() {
        Stock s = new Stock(1, "AAPL", "Apple", new BigDecimal("100.00"), "Tech");
        assertEquals(new BigDecimal("-500.00"), new BuyOrder(1, s, 5).cashImpact());
        assertEquals(new BigDecimal("500.00"), new SellOrder(1, s, 5).cashImpact());
    }
}
