package com.papertrade.service;

import static org.junit.jupiter.api.Assertions.*;

import com.papertrade.dao.*;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.*;
import com.papertrade.model.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;

/** End-to-end tests against a real MySQL database. Skipped automatically when the DB is unreachable. */
class TradingIntegrationTest {
    static final String PASSWORD = "Passw0rd123";
    static final UserDAO USERS = new UserDAO();
    static final List<String> createdEmails = new ArrayList<>();
    static AuthService auth;
    static MarketService market;
    static TradingService trading;
    static PortfolioService portfolio;
    static TransactionService transactions;

    @BeforeAll
    static void setUp() {
        try (Connection ignored = DatabaseConnection.getConnection()) {
            market = MarketService.loadFromDatabase(new StockDAO());
        } catch (RuntimeException e) {
            Assumptions.abort("MySQL not reachable / not initialised: " + e.getMessage());
        } catch (Exception e) {
            Assumptions.abort("MySQL error: " + e.getMessage());
        }
        HoldingDAO holdings = new HoldingDAO();
        auth = new AuthService(USERS);
        trading = new TradingService(market, USERS, holdings, new OrderDAO(), new TransactionDAO());
        portfolio = new PortfolioService(market, USERS, holdings, new PortfolioHistoryDAO());
        transactions = new TransactionService(new TransactionDAO());
    }

    @AfterAll
    static void cleanUp() throws Exception {
        if (createdEmails.isEmpty()) return;
        try (Connection c = DatabaseConnection.getConnection()) {
            for (String e : createdEmails) USERS.deleteByEmail(c, e);
        }
    }

    private User newUser() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return auth.register("Test User", email, PASSWORD, PASSWORD);
    }

    private BigDecimal balance(User u) throws Exception { return auth.getUser(u.getId()).getBalance(); }

    @Test
    void registrationGivesInitialBalanceAndHashedPassword() throws Exception {
        User u = newUser();
        assertEquals(0, User.INITIAL_BALANCE.compareTo(balance(u)));
        assertNotEquals(PASSWORD, u.getPasswordHash());
        assertTrue(u.getId() > 0);
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        User u = newUser();
        ValidationException e = assertThrows(ValidationException.class,
                () -> auth.register("Other", u.getEmail().toUpperCase(), PASSWORD, PASSWORD));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void registrationValidatesInput() {
        assertThrows(ValidationException.class, () -> auth.register("", "a@b.com", PASSWORD, PASSWORD));
        assertThrows(ValidationException.class, () -> auth.register("N", "bad-email", PASSWORD, PASSWORD));
        assertThrows(ValidationException.class, () -> auth.register("N", "a@b.com", "short", "short"));
        assertThrows(ValidationException.class, () -> auth.register("N", "a@b.com", PASSWORD, "Different1"));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        User u = newUser();
        assertEquals(u.getId(), auth.login(u.getEmail(), PASSWORD).getId());
    }

    @Test
    void invalidLoginIsRejected() throws Exception {
        User u = newUser();
        assertThrows(UserNotFoundException.class, () -> auth.login(u.getEmail(), "WrongPass1"));
        assertThrows(UserNotFoundException.class, () -> auth.login("nobody@example.com", PASSWORD));
    }

    @Test
    void buyReducesBalanceCreatesHoldingOrderAndTransaction() throws Exception {
        User u = newUser();
        Stock aapl = market.getStock("AAPL");
        Transaction tx = trading.buy(u.getId(), "AAPL", 10);
        BigDecimal cost = aapl.getCurrentPrice().multiply(BigDecimal.TEN);

        assertEquals(0, User.INITIAL_BALANCE.subtract(cost).compareTo(balance(u)));
        Portfolio p = portfolio.getPortfolio(u.getId());
        assertEquals(1, p.getHoldings().size());
        assertEquals(10, p.getHoldings().get(0).getQuantity());
        assertEquals(1, transactions.getHistory(u.getId()).size());
        assertEquals(TradeType.BUY, tx.getType());
        // portfolio value is unchanged by a buy at an unchanged price
        assertEquals(0, User.INITIAL_BALANCE.compareTo(p.totalValue()));
        assertEquals(0, p.unrealizedProfitLoss().signum());
    }

    @Test
    void secondBuyUpdatesAverageBuyPrice() throws Exception {
        User u = newUser();
        Stock s = market.getStock("MSFT");
        trading.buy(u.getId(), "MSFT", 2);
        market.updatePrice("MSFT", s.getCurrentPrice().add(new BigDecimal("100.00")));
        try {
            trading.buy(u.getId(), "MSFT", 2);
            Holding h = portfolio.getPortfolio(u.getId()).getHoldings().get(0);
            assertEquals(4, h.getQuantity());
            assertEquals(0, s.getCurrentPrice().add(new BigDecimal("50.00"))
                    .compareTo(h.getAverageBuyPrice().setScale(2)));
        } finally {
            market.updatePrice("MSFT", s.getCurrentPrice()); // restore shared in-memory price
        }
    }

    @Test
    void sellIncreasesBalanceAndReducesHolding() throws Exception {
        User u = newUser();
        trading.buy(u.getId(), "NVDA", 10);
        BigDecimal before = balance(u);
        trading.sell(u.getId(), "NVDA", 4);
        BigDecimal proceeds = market.getStock("NVDA").getCurrentPrice().multiply(BigDecimal.valueOf(4));
        assertEquals(0, before.add(proceeds).compareTo(balance(u)));
        assertEquals(6, portfolio.getPortfolio(u.getId()).getHoldings().get(0).getQuantity());
    }

    @Test
    void sellingEverythingRemovesTheHolding() throws Exception {
        User u = newUser();
        trading.buy(u.getId(), "TSLA", 3);
        trading.sell(u.getId(), "TSLA", 3);
        assertTrue(portfolio.getPortfolio(u.getId()).getHoldings().isEmpty());
        assertEquals(2, transactions.getHistory(u.getId()).size());
    }

    @Test
    void insufficientBalanceIsRejectedAndNothingChanges() throws Exception {
        User u = newUser();
        InsufficientBalanceException e = assertThrows(InsufficientBalanceException.class,
                () -> trading.buy(u.getId(), "META", 1_000_000));
        assertTrue(e.getRequired().compareTo(e.getAvailable()) > 0);
        assertEquals(0, User.INITIAL_BALANCE.compareTo(balance(u)));
        assertTrue(portfolio.getPortfolio(u.getId()).getHoldings().isEmpty());
        assertTrue(transactions.getHistory(u.getId()).isEmpty());
        try (Connection c = DatabaseConnection.getConnection()) { // rejection is audited
            assertEquals(1, new OrderDAO().countByUserAndStatus(c, u.getId(), "REJECTED"));
        }
    }

    @Test
    void insufficientSharesIsRejected() throws Exception {
        User u = newUser();
        assertThrows(InsufficientSharesException.class, () -> trading.sell(u.getId(), "AAPL", 1));
        trading.buy(u.getId(), "AAPL", 2);
        assertThrows(InsufficientSharesException.class, () -> trading.sell(u.getId(), "AAPL", 3));
        assertEquals(2, portfolio.getPortfolio(u.getId()).getHoldings().get(0).getQuantity());
    }

    @Test
    void invalidOrdersAreRejected() throws Exception {
        User u = newUser();
        assertThrows(InvalidOrderException.class, () -> trading.buy(u.getId(), "AAPL", 0));
        assertThrows(InvalidOrderException.class, () -> trading.sell(u.getId(), "AAPL", -5));
        assertThrows(StockNotFoundException.class, () -> trading.buy(u.getId(), "NOPE", 1));
    }

    @Test
    void snapshotAndExportWork() throws Exception {
        User u = newUser();
        trading.buy(u.getId(), "AMZN", 1);
        portfolio.recordSnapshot(u.getId());
        assertEquals(1, portfolio.getHistory(u.getId(), 10).size());

        Path dir = Files.createTempDirectory("papertrade-test");
        Path file = transactions.exportToFile(u, dir);
        String content = Files.readString(file);
        assertTrue(content.contains("AMZN") && content.contains("BUY"));
        Files.delete(file);
        Files.delete(dir);
    }
}
