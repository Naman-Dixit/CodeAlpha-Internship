package com.papertrade.service;

import com.papertrade.dao.HoldingDAO;
import com.papertrade.dao.OrderDAO;
import com.papertrade.dao.TransactionDAO;
import com.papertrade.dao.UserDAO;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.InsufficientBalanceException;
import com.papertrade.exception.InsufficientSharesException;
import com.papertrade.exception.InvalidOrderException;
import com.papertrade.exception.PaperTradeException;
import com.papertrade.exception.UserNotFoundException;
import com.papertrade.market.MarketDataProvider;
import com.papertrade.model.BuyOrder;
import com.papertrade.model.Holding;
import com.papertrade.model.Order;
import com.papertrade.model.OrderStatus;
import com.papertrade.model.SellOrder;
import com.papertrade.model.Stock;
import com.papertrade.model.Transaction;
import com.papertrade.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Executes buy/sell orders. Every order is ONE JDBC transaction: commit on success, rollback on any failure. */
public class TradingService {
    private final MarketDataProvider market;
    private final UserDAO userDAO;
    private final HoldingDAO holdingDAO;
    private final OrderDAO orderDAO;
    private final TransactionDAO transactionDAO;

    public TradingService(MarketDataProvider market, UserDAO userDAO, HoldingDAO holdingDAO,
                          OrderDAO orderDAO, TransactionDAO transactionDAO) {
        this.market = market;
        this.userDAO = userDAO;
        this.holdingDAO = holdingDAO;
        this.orderDAO = orderDAO;
        this.transactionDAO = transactionDAO;
    }

    public Transaction buy(int userId, String symbol, int quantity) throws PaperTradeException {
        return execute(userId, symbol, quantity, true);
    }

    public Transaction sell(int userId, String symbol, int quantity) throws PaperTradeException {
        return execute(userId, symbol, quantity, false);
    }

    private Transaction execute(int userId, String symbol, int quantity, boolean isBuy) throws PaperTradeException {
        if (quantity <= 0) throw new InvalidOrderException("Quantity must be a positive whole number.");
        Stock stock = market.getStock(symbol); // price is fixed here for the whole order
        if (stock.getCurrentPrice().signum() <= 0) throw new InvalidOrderException("Invalid price for " + symbol);
        Order order = isBuy ? new BuyOrder(userId, stock, quantity) : new SellOrder(userId, stock, quantity);

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Transaction tx = process(c, order);
                c.commit();
                return tx;
            } catch (InsufficientBalanceException | InsufficientSharesException e) {
                rollbackQuietly(c);
                recordRejected(order); // audit trail, written in its own small transaction
                throw e;
            } catch (PaperTradeException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw new DatabaseException("Order failed and was rolled back (database error).", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Order failed due to a database error.", e);
        }
    }

    private Transaction process(Connection c, Order order) throws SQLException, PaperTradeException {
        int userId = order.getUserId();
        Stock stock = order.getStock();
        int qty = order.getQuantity();
        BigDecimal total = order.totalAmount();

        User user = userDAO.findByIdForUpdate(c, userId)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
        Optional<Holding> held = holdingDAO.findByUserAndStockForUpdate(c, userId, stock.getId());

        switch (order.getType()) {
            case BUY -> {
                if (user.getBalance().compareTo(total) < 0) {
                    throw new InsufficientBalanceException(total, user.getBalance());
                }
                if (held.isPresent()) {
                    Holding h = held.get();
                    BigDecimal avg = Holding.weightedAverage(h.getQuantity(), h.getAverageBuyPrice(), qty, order.getPrice());
                    holdingDAO.update(c, h.getId(), h.getQuantity() + qty, avg);
                } else {
                    holdingDAO.insert(c, userId, stock.getId(), qty, order.getPrice().setScale(4));
                }
            }
            case SELL -> {
                int owned = held.map(Holding::getQuantity).orElse(0);
                if (owned < qty) throw new InsufficientSharesException(stock.getSymbol(), qty, owned);
                Holding h = held.orElseThrow();
                if (owned == qty) holdingDAO.delete(c, h.getId());
                else holdingDAO.update(c, h.getId(), owned - qty, h.getAverageBuyPrice());
            }
        }

        userDAO.updateBalance(c, userId, user.getBalance().add(order.cashImpact()));
        order.setStatus(OrderStatus.EXECUTED);
        orderDAO.insert(c, order);
        Transaction tx = new Transaction(0, userId, stock, order.getType(), qty, order.getPrice(), total,
                LocalDateTime.now());
        transactionDAO.insert(c, tx);
        return tx;
    }

    private void recordRejected(Order order) {
        order.setStatus(OrderStatus.REJECTED);
        try (Connection c = DatabaseConnection.getConnection()) {
            orderDAO.insert(c, order);
        } catch (SQLException | DatabaseException ignored) {
            // the rejection itself is what the user must see; the audit row is best-effort
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // connection is closed right after; nothing more we can do
        }
    }
}
