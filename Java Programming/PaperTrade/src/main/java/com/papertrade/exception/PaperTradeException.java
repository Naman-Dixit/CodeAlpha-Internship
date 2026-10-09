package com.papertrade.exception;

/** Base class for all expected, user-presentable business errors. */
public class PaperTradeException extends Exception {
    public PaperTradeException(String message) { super(message); }
}
