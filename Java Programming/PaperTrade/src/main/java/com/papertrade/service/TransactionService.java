package com.papertrade.service;

import com.papertrade.dao.TransactionDAO;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.PaperTradeException;
import com.papertrade.model.Transaction;
import com.papertrade.model.User;
import com.papertrade.util.ConsoleFormatter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransactionService {
    private final TransactionDAO transactionDAO;

    public TransactionService(TransactionDAO transactionDAO) { this.transactionDAO = transactionDAO; }

    public List<Transaction> getHistory(int userId) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return transactionDAO.findByUser(c, userId);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load transaction history.", e);
        }
    }

    /** Writes transaction_history_&lt;user&gt;.txt into the given directory and returns its path. */
    public Path exportToFile(User user, Path directory) throws PaperTradeException {
        String safeName = user.getName().replaceAll("[^A-Za-z0-9]+", "_");
        Path file = directory.resolve("transaction_history_" + safeName + ".txt");
        List<Transaction> history = getHistory(user.getId());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("PAPERTRADE - TRANSACTION HISTORY (simulated paper trading)\n");
            w.write("Account:  " + user.getName() + " <" + user.getEmail() + ">\n");
            w.write("Exported: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");
            w.write(ConsoleFormatter.transactionTable(history));
            w.write("\nTotal transactions: " + history.size() + "\n");
        } catch (IOException e) {
            throw new PaperTradeException("Could not write export file: " + e.getMessage());
        }
        return file;
    }
}
