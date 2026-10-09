package com.papertrade.market;

import com.papertrade.model.Stock;
import com.papertrade.service.MarketService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically applies a bounded random walk to every stock price on a background thread:
 * newPrice = oldPrice x (1 + r), r uniform in [-maxChange, +maxChange]. Prices never drop below MIN_PRICE.
 */
public class MarketPriceSimulator {
    static final BigDecimal MIN_PRICE = new BigDecimal("1.00");
    private static final Logger LOG = Logger.getLogger(MarketPriceSimulator.class.getName());

    private final MarketService market;
    private final Consumer<List<Stock>> persister; // may be null (e.g. in tests)
    private final double maxChange;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "market-simulator");
        t.setDaemon(true);
        return t;
    });

    public MarketPriceSimulator(MarketService market, Consumer<List<Stock>> persister, double maxChange) {
        this.market = market;
        this.persister = persister;
        this.maxChange = maxChange;
    }

    public void start(long intervalSeconds) {
        executor.scheduleAtFixedRate(this::tick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /** One simulation step. An exception must never kill the scheduled task, so it is caught here. */
    public void tick() {
        try {
            for (Stock s : market.getAllStocks()) {
                double change = ThreadLocalRandom.current().nextDouble(-maxChange, maxChange);
                market.updatePrice(s.getSymbol(), nextPrice(s.getCurrentPrice(), change));
            }
            if (persister != null) persister.accept(market.getAllStocks());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Price update failed: " + e.getMessage());
        }
    }

    static BigDecimal nextPrice(BigDecimal oldPrice, double changeFraction) {
        BigDecimal next = oldPrice.multiply(BigDecimal.valueOf(1 + changeFraction)).setScale(2, RoundingMode.HALF_UP);
        return next.compareTo(MIN_PRICE) < 0 ? MIN_PRICE : next;
    }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
