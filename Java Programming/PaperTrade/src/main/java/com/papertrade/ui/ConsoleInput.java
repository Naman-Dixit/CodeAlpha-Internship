package com.papertrade.ui;

import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.PaperTradeException;

import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared console I/O helper: prompting plus uniform, stack-trace-free error reporting. */
public class ConsoleInput {
    private static final Logger LOG = Logger.getLogger(ConsoleInput.class.getName());
    private final Scanner scanner = new Scanner(System.in);

    @FunctionalInterface
    public interface Action { void run() throws PaperTradeException; }

    /** Prompts and returns the trimmed line; throws NoSuchElementException on end of input. */
    public String ask(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public void pause() { ask("\nPress Enter to continue..."); }

    /** Runs an action; business errors become friendly messages, DB errors are logged but not shown raw. */
    public boolean safely(Action action) {
        try {
            action.run();
            return true;
        } catch (PaperTradeException e) {
            System.out.println("\nERROR: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("\nERROR: " + e.getMessage() + " Please try again.");
            LOG.log(Level.FINE, "Database failure", e);
        }
        return false;
    }
}
