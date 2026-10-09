package com.papertrade.service;

import com.papertrade.dao.HoldingDAO;
import com.papertrade.dao.PortfolioHistoryDAO;
import com.papertrade.dao.UserDAO;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.PaperTradeException;
import com.papertrade.exception.UserNotFoundException;
import com.papertrade.market.MarketDataProvider;
import com.papertrade.model.Holding;
import com.papertrade.model.Portfolio;
import com.papertrade.model.PortfolioSnapshot;
import com.papertrade.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PortfolioService {
    private static final Logger LOG = Logger.getLogger(PortfolioService.class.getName());

    private final MarketDataProvider market;
    private final UserDAO userDAO;
    private final HoldingDAO holdingDAO;
    private final PortfolioHistoryDAO historyDAO;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "portfolio-snapshots");
        t.setDaemon(true);
        return t;
    });

    public PortfolioService(MarketDataProvider market, UserDAO userDAO, HoldingDAO holdingDAO,
                            PortfolioHistoryDAO historyDAO) {
        this.market = market;
        this.userDAO = userDAO;
        this.holdingDAO = holdingDAO;
        this.historyDAO = historyDAO;
    }

    /** Builds the portfolio from the database, valuing holdings at the live simulated prices. */
    public Portfolio getPortfolio(int userId) throws PaperTradeException {
        try (Connection c = DatabaseConnection.getConnection()) {
            return buildPortfolio(c, userId);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load portfolio.", e);
        }
    }

    public void recordSnapshot(int userId) throws PaperTradeException {
        try (Connection c = DatabaseConnection.getConnection()) {
            Portfolio p = buildPortfolio(c, userId);
            historyDAO.insert(c, userId, p.stockValue(), p.getCash(), p.totalValue());
        } catch (SQLException e) {
            throw new DatabaseException("Could not record portfolio snapshot.", e);
        }
    }

    public List<PortfolioSnapshot> getHistory(int userId, int limit) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return historyDAO.findRecent(c, userId, limit);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load portfolio history.", e);
        }
    }

    /** Starts the background job that snapshots every user's portfolio at a fixed interval. */
    public void startSnapshotScheduler(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::snapshotAllUsers, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    void snapshotAllUsers() {
        try (Connection c = DatabaseConnection.getConnection()) {
            for (int id : userDAO.findAllIds(c)) {
                Portfolio p = buildPortfolio(c, id);
                historyDAO.insert(c, id, p.stockValue(), p.getCash(), p.totalValue());
            }
        } catch (SQLException | PaperTradeException | RuntimeException e) {
            LOG.log(Level.WARNING, "Scheduled snapshot failed: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Portfolio buildPortfolio(Connection c, int userId) throws SQLException, PaperTradeException {
        User user = userDAO.findById(c, userId).orElseThrow(() -> new UserNotFoundException("User not found."));
        List<Holding> holdings = holdingDAO.findByUser(c, userId);
        for (Holding h : holdings) h.setStock(market.getStockById(h.getStock().getId()));
        return new Portfolio(user.getBalance(), holdings);
    }
}
