package com.papertrade.exception;

public class StockNotFoundException extends PaperTradeException {
    public StockNotFoundException(String symbol) { super("Stock not found: " + symbol); }
}
