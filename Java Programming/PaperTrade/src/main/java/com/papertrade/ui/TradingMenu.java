package com.papertrade.ui;

import com.papertrade.exception.ValidationException;
import com.papertrade.market.MarketDataProvider;
import com.papertrade.model.Portfolio;
import com.papertrade.model.PortfolioSnapshot;
import com.papertrade.model.Stock;
import com.papertrade.model.Transaction;
import com.papertrade.model.User;
import com.papertrade.service.AuthService;
import com.papertrade.service.PortfolioService;
import com.papertrade.service.TradingService;
import com.papertrade.service.TransactionService;
import com.papertrade.util.ConsoleFormatter;
import com.papertrade.util.InputValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.papertrade.util.ConsoleFormatter.money;

/** The logged-in dashboard and all trading screens. */
public class TradingMenu {
    private final ConsoleInput in;
    private final AuthService authService;
    private final MarketDataProvider market;
    private final TradingService tradingService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;

    public TradingMenu(ConsoleInput in, AuthService authService, MarketDataProvider market,
                       TradingService tradingService, PortfolioService portfolioService,
                       TransactionService transactionService) {
        this.in = in;
        this.authService = authService;
        this.market = market;
        this.tradingService = tradingService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
    }

    public void run(User user) {
        in.safely(() -> portfolioService.recordSnapshot(user.getId()));
        while (true) {
            showDashboardHeader(user);
            System.out.println("""

                    1. View Market
                    2. Buy Stock
                    3. Sell Stock
                    4. View Portfolio
                    5. Portfolio Performance
                    6. Transaction History
                    7. Account Details
                    8. Logout
                    9. Export Transaction History
                    0. Back (Logout)""");
            switch (InputValidator.parseMenuChoice(in.ask("\nEnter choice: "))) {
                case 1 -> viewMarket();
                case 2 -> trade(user, true);
                case 3 -> trade(user, false);
                case 4 -> viewPortfolio(user);
                case 5 -> viewPerformance(user);
                case 6 -> viewTransactions(user);
                case 7 -> viewAccount(user);
                case 9 -> export(user);
                case 8, 0 -> {
                    in.safely(() -> portfolioService.recordSnapshot(user.getId()));
                    System.out.println("\nLogged out. See you soon, " + user.getName() + "!");
                    return;
                }
                default -> System.out.println("\nInvalid choice. Please enter a number from the menu.");
            }
        }
    }

    private void showDashboardHeader(User user) {
        System.out.println(ConsoleFormatter.title("PAPERTRADE DASHBOARD"));
        System.out.println("\nWelcome, " + user.getName());
        in.safely(() -> {
            Portfolio p = portfolioService.getPortfolio(user.getId());
            System.out.println("\nCash Balance:    " + money(p.getCash()));
            System.out.println("Portfolio Value: " + money(p.totalValue()));
            System.out.println("Total P/L:       " + ConsoleFormatter.signedMoney(p.accountProfitLoss())
                    + "  (" + ConsoleFormatter.percent(p.accountReturnPercent()) + ")");
        });
    }

    private void viewMarket() {
        System.out.println(ConsoleFormatter.section("MARKET"));
        System.out.println(ConsoleFormatter.marketTable(market.getAllStocks()));
        System.out.println("Prices are SIMULATED and change every few seconds. They are not real market data.");
        in.pause();
    }

    private void trade(User user, boolean isBuy) {
        String verb = isBuy ? "BUY" : "SELL";
        System.out.println(ConsoleFormatter.section(verb + " STOCK") + "  (enter 0 to go back)");
        String rawSymbol = in.ask("Enter stock symbol: ");
        if (rawSymbol.equals("0")) return;
        in.safely(() -> {
            Stock stock = market.getStock(InputValidator.normalizeSymbol(rawSymbol));
            System.out.println(stock.getSymbol() + " | " + stock.getCompanyName() + " | current price "
                    + money(stock.getCurrentPrice()));
            String rawQty = in.ask("Enter quantity: ");
            if (rawQty.equals("0")) return;
            int qty = InputValidator.parsePositiveInt(rawQty, "Quantity");
            Transaction tx = isBuy ? tradingService.buy(user.getId(), stock.getSymbol(), qty)
                                   : tradingService.sell(user.getId(), stock.getSymbol(), qty);
            System.out.println("\nSUCCESS: " + (isBuy ? "Bought " : "Sold ") + tx.getQuantity() + " x "
                    + tx.getStock().getSymbol() + " @ " + money(tx.getPrice()));
            System.out.println("Total " + (isBuy ? "cost: " : "proceeds: ") + money(tx.getTotalAmount()));
            portfolioService.recordSnapshot(user.getId());
        });
        in.pause();
    }

    private void viewPortfolio(User user) {
        in.safely(() -> {
            Portfolio p = portfolioService.getPortfolio(user.getId());
            System.out.println(ConsoleFormatter.section("HOLDINGS"));
            if (p.getHoldings().isEmpty()) {
                System.out.println("\nYou do not own any stocks yet. Use 'Buy Stock' to get started.");
            } else {
                System.out.println(ConsoleFormatter.holdingsTable(p));
                System.out.println("\nPer-stock profit / loss:");
                p.getHoldings().forEach(h -> System.out.printf("  %-7s %s%n", h.getStock().getSymbol(),
                        ConsoleFormatter.signedMoney(h.profitLoss())));
            }
            System.out.println(ConsoleFormatter.section("PORTFOLIO VALUE"));
            System.out.printf("Cash Balance:       %s%n", money(p.getCash()));
            System.out.printf("Stock Value:        %s%n", money(p.stockValue()));
            System.out.printf("Total Portfolio:    %s%n", money(p.totalValue()));
            System.out.printf("Invested Capital:   %s%n", money(p.investedValue()));
            System.out.printf("Unrealised P/L:     %s (%s)%n", ConsoleFormatter.signedMoney(p.unrealizedProfitLoss()),
                    ConsoleFormatter.percent(p.returnPercent()));
            System.out.printf("Account P/L:        %s (%s vs starting capital)%n",
                    ConsoleFormatter.signedMoney(p.accountProfitLoss()), ConsoleFormatter.percent(p.accountReturnPercent()));
        });
        in.pause();
    }

    private void viewPerformance(User user) {
        in.safely(() -> portfolioService.recordSnapshot(user.getId())); // make sure "now" is included
        List<PortfolioSnapshot> history = portfolioService.getHistory(user.getId(), 20);
        System.out.println(ConsoleFormatter.section("PORTFOLIO PERFORMANCE"));
        if (history.isEmpty()) {
            System.out.println("\nNo snapshots recorded yet.");
        } else {
            System.out.println(ConsoleFormatter.performanceTable(history));
            BigDecimal first = history.get(0).totalValue();
            BigDecimal last = history.get(history.size() - 1).totalValue();
            BigDecimal high = history.stream().map(PortfolioSnapshot::totalValue).max(BigDecimal::compareTo).orElse(last);
            BigDecimal low = history.stream().map(PortfolioSnapshot::totalValue).min(BigDecimal::compareTo).orElse(last);
            BigDecimal change = last.subtract(first);
            BigDecimal pct = first.signum() == 0 ? BigDecimal.ZERO
                    : change.multiply(BigDecimal.valueOf(100)).divide(first, 2, RoundingMode.HALF_UP);
            System.out.println("Showing the last " + history.size() + " snapshot(s).");
            System.out.printf("First: %s   Latest: %s   High: %s   Low: %s%n", money(first), money(last), money(high), money(low));
            System.out.printf("Change over period: %s (%s)%n", ConsoleFormatter.signedMoney(change), ConsoleFormatter.percent(pct));
        }
        in.pause();
    }

    private void viewTransactions(User user) {
        in.safely(() -> {
            List<Transaction> list = transactionService.getHistory(user.getId());
            System.out.println(ConsoleFormatter.section("TRANSACTION HISTORY"));
            if (list.isEmpty()) System.out.println("\nNo transactions yet.");
            else System.out.println(ConsoleFormatter.transactionTable(list));
        });
        in.pause();
    }

    private void viewAccount(User user) {
        in.safely(() -> {
            User fresh = authService.getUser(user.getId());
            System.out.println(ConsoleFormatter.section("ACCOUNT DETAILS"));
            System.out.println("Name:         " + fresh.getName());
            System.out.println("Email:        " + fresh.getEmail());
            System.out.println("Member since: " + fresh.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            System.out.println("Cash balance: " + money(fresh.getBalance()));
            System.out.println("\nThis is a paper-trading simulation. All money is virtual.");
        });
        in.pause();
    }

    private void export(User user) {
        in.safely(() -> {
            Path file = transactionService.exportToFile(user, Path.of("."));
            System.out.println("\nExported to: " + file.toAbsolutePath().normalize());
        });
        in.pause();
    }
}
