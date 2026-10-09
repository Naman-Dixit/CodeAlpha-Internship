package com.papertrade;

import com.papertrade.dao.HoldingDAO;
import com.papertrade.dao.OrderDAO;
import com.papertrade.dao.PortfolioHistoryDAO;
import com.papertrade.dao.StockDAO;
import com.papertrade.dao.TransactionDAO;
import com.papertrade.dao.UserDAO;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.DatabaseException;
import com.papertrade.market.MarketPriceSimulator;
import com.papertrade.service.AuthService;
import com.papertrade.service.MarketService;
import com.papertrade.service.PortfolioService;
import com.papertrade.service.TradingService;
import com.papertrade.service.TransactionService;
import com.papertrade.ui.AuthMenu;
import com.papertrade.ui.ConsoleInput;
import com.papertrade.ui.MainMenu;
import com.papertrade.ui.TradingMenu;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // UTF-8 so the ₹ symbol renders correctly on every platform
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        UserDAO userDAO = new UserDAO();
        StockDAO stockDAO = new StockDAO();
        HoldingDAO holdingDAO = new HoldingDAO();

        MarketService market;
        try {
            market = MarketService.loadFromDatabase(stockDAO);
        } catch (DatabaseException e) {
            System.out.println("\nERROR: " + e.getMessage());
            if (e.getCause() != null) System.out.println("Cause: " + e.getCause().getMessage());
            System.out.println("See README.md (MySQL setup) for instructions.");
            return;
        }

        PortfolioService portfolioService = new PortfolioService(market, userDAO, holdingDAO, new PortfolioHistoryDAO());
        MarketPriceSimulator simulator = new MarketPriceSimulator(market, stocks -> {
            try (Connection c = DatabaseConnection.getConnection()) {
                stockDAO.updatePrices(c, stocks);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Could not persist prices: " + e.getMessage());
            }
        }, 0.02);
        simulator.start(DatabaseConnection.getIntProperty("market.update.seconds", 5));
        portfolioService.startSnapshotScheduler(DatabaseConnection.getIntProperty("snapshot.interval.seconds", 60));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            simulator.stop();
            portfolioService.stop();
        }));

        AuthService authService = new AuthService(userDAO);
        TradingService tradingService = new TradingService(market, userDAO, holdingDAO, new OrderDAO(), new TransactionDAO());
        TransactionService transactionService = new TransactionService(new TransactionDAO());

        ConsoleInput in = new ConsoleInput();
        TradingMenu tradingMenu = new TradingMenu(in, authService, market, tradingService, portfolioService, transactionService);
        try {
            new MainMenu(in, new AuthMenu(authService, in), tradingMenu).run();
        } catch (NoSuchElementException endOfInput) {
            System.out.println("\nInput closed. Goodbye!");
        } finally {
            simulator.stop();
            portfolioService.stop();
        }
    }
}
