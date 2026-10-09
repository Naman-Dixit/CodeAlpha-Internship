package com.papertrade.exception;

public class InsufficientSharesException extends PaperTradeException {
    public InsufficientSharesException(String symbol, int requested, int owned) {
        super("Insufficient shares of " + symbol + ".\n\nRequested: " + requested + "\nOwned:     " + owned);
    }
}
