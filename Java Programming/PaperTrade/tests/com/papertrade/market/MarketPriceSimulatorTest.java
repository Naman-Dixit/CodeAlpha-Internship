package com.papertrade.market;

import static org.junit.jupiter.api.Assertions.*;

import com.papertrade.model.Stock;
import com.papertrade.service.MarketService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MarketPriceSimulatorTest {
    private static MarketService market() {
        return new MarketService(List.of(
                new Stock(1, "AAPL", "Apple", new BigDecimal("220.45"), "Technology"),
                new Stock(2, "PENNY", "Penny Co", new BigDecimal("1.00"), "Other")));
    }

    @Test
    void nextPriceAppliesPercentageChange() {
        assertEquals(new BigDecimal("1012.00"), MarketPriceSimulator.nextPrice(new BigDecimal("1000"), 0.012));
    }

    @Test
    void priceNeverBecomesZeroOrNegative() {
        assertEquals(MarketPriceSimulator.MIN_PRICE, MarketPriceSimulator.nextPrice(new BigDecimal("1.00"), -0.5));
        assertEquals(MarketPriceSimulator.MIN_PRICE, MarketPriceSimulator.nextPrice(new BigDecimal("0.50"), -0.99));
    }

    @Test
    void manyTicksKeepPricesPositiveAndWithinBounds() {
        MarketService m = market();
        MarketPriceSimulator sim = new MarketPriceSimulator(m, null, 0.02);
        BigDecimal before = m.getAllStocks().get(0).getCurrentPrice();
        sim.tick();
        BigDecimal after = m.getAllStocks().get(0).getCurrentPrice();
        assertTrue(after.subtract(before).abs().compareTo(before.multiply(new BigDecimal("0.0201"))) <= 0);
        for (int i = 0; i < 2000; i++) sim.tick();
        m.getAllStocks().forEach(s -> assertTrue(s.getCurrentPrice().compareTo(MarketPriceSimulator.MIN_PRICE) >= 0));
    }

    @Test
    void persisterReceivesUpdatedStocks() {
        List<List<Stock>> calls = new ArrayList<>();
        new MarketPriceSimulator(market(), calls::add, 0.02).tick();
        assertEquals(1, calls.size());
        assertEquals(2, calls.get(0).size());
    }

    @Test
    void scheduledExecutorRunsAndStops() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        MarketPriceSimulator sim = new MarketPriceSimulator(market(), stocks -> ran.countDown(), 0.02);
        sim.start(1);
        assertTrue(ran.await(3, TimeUnit.SECONDS), "scheduler should have ticked");
        sim.stop();
    }

    @Test
    void concurrentReadersAndWriterDoNotFail() throws Exception {
        MarketService m = market();
        MarketPriceSimulator sim = new MarketPriceSimulator(m, null, 0.02);
        Thread writer = new Thread(() -> { for (int i = 0; i < 5000; i++) sim.tick(); });
        List<Throwable> errors = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    for (Stock s : m.getAllStocks()) assertTrue(s.getCurrentPrice().signum() > 0);
                }
            } catch (Throwable t) { synchronized (errors) { errors.add(t); } }
        });
        writer.start(); reader.start(); writer.join(); reader.join();
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void unknownSymbolIsRejected() {
        assertThrows(com.papertrade.exception.StockNotFoundException.class, () -> market().getStock("ZZZZ"));
    }
}
