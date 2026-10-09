package com.papertrade.util;

import com.papertrade.model.Holding;
import com.papertrade.model.Portfolio;
import com.papertrade.model.PortfolioSnapshot;
import com.papertrade.model.Stock;
import com.papertrade.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Text rendering helpers shared by the console UI and the file export. */
public final class ConsoleFormatter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LINE = "=".repeat(78);
    private static final String DASH = "-".repeat(78);

    private ConsoleFormatter() { }

    /** Formats with the Indian digit grouping, e.g. 1000000 -> ₹10,00,000.00 */
    public static String money(BigDecimal value) {
        BigDecimal s = value.setScale(2, RoundingMode.HALF_UP);
        boolean negative = s.signum() < 0;
        String[] parts = s.abs().toPlainString().split("\\.");
        String integer = parts[0];
        String grouped;
        if (integer.length() <= 3) {
            grouped = integer;
        } else {
            String last3 = integer.substring(integer.length() - 3);
            String rest = integer.substring(0, integer.length() - 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) sb.append(',');
                sb.append(rest.charAt(i));
            }
            grouped = sb + "," + last3;
        }
        return (negative ? "-" : "") + "₹" + grouped + "." + parts[1];
    }

    public static String percent(BigDecimal value) {
        return String.format("%+.2f%%", value);
    }

    public static String signedMoney(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + money(value);
    }

    public static String title(String text) {
        int pad = Math.max(0, (40 - text.length()) / 2);
        return "\n" + "=".repeat(40) + "\n" + " ".repeat(pad) + text + "\n" + "=".repeat(40);
    }

    public static String section(String text) {
        return "\n----------- " + text + " -----------";
    }

    public static String marketTable(List<Stock> stocks) {
        StringBuilder sb = new StringBuilder(LINE).append('\n');
        sb.append(String.format(" %-8s %-18s %16s   %-14s%n", "SYMBOL", "COMPANY", "PRICE", "SECTOR"));
        sb.append(LINE).append('\n');
        for (Stock s : stocks) {
            sb.append(String.format(" %-8s %-18s %16s   %-14s%n", s.getSymbol(), s.getCompanyName(),
                    money(s.getCurrentPrice()), s.getSector()));
        }
        return sb.append(LINE).toString();
    }

    public static String holdingsTable(Portfolio p) {
        StringBuilder sb = new StringBuilder(LINE).append('\n');
        sb.append(String.format(" %-7s %5s %13s %13s %14s %14s %9s%n",
                "SYMBOL", "QTY", "AVG BUY", "PRICE", "INVESTED", "VALUE", "P/L %"));
        sb.append(LINE).append('\n');
        for (Holding h : p.getHoldings()) {
            sb.append(String.format(" %-7s %5d %13s %13s %14s %14s %9s%n", h.getStock().getSymbol(),
                    h.getQuantity(), money(h.getAverageBuyPrice().setScale(2, RoundingMode.HALF_UP)),
                    money(h.getStock().getCurrentPrice()), money(h.investedValue()), money(h.currentValue()),
                    percent(h.profitLossPercent())));
        }
        return sb.append(LINE).toString();
    }

    public static String transactionTable(List<Transaction> list) {
        StringBuilder sb = new StringBuilder(LINE).append('\n');
        sb.append(String.format(" %-19s %-7s %-5s %5s %14s %16s%n", "DATE", "STOCK", "TYPE", "QTY", "PRICE", "TOTAL"));
        sb.append(LINE).append('\n');
        for (Transaction t : list) {
            sb.append(String.format(" %-19s %-7s %-5s %5d %14s %16s%n", t.getTimestamp().format(DATE_TIME),
                    t.getStock().getSymbol(), t.getType(), t.getQuantity(), money(t.getPrice()),
                    money(t.getTotalAmount())));
        }
        return sb.append(LINE).toString();
    }

    public static String performanceTable(List<PortfolioSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder(DASH).append('\n');
        sb.append(String.format(" %-19s %16s %16s %16s%n", "TIME", "CASH", "STOCK VALUE", "TOTAL"));
        sb.append(DASH).append('\n');
        for (PortfolioSnapshot s : snapshots) {
            sb.append(String.format(" %-19s %16s %16s %16s%n", s.timestamp().format(DATE_TIME),
                    money(s.cashBalance()), money(s.stockValue()), money(s.totalValue())));
        }
        return sb.append(DASH).toString();
    }
}
