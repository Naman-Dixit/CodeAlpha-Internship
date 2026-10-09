package com.papertrade.exception;

import com.papertrade.util.ConsoleFormatter;
import java.math.BigDecimal;

public class InsufficientBalanceException extends PaperTradeException {
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientBalanceException(BigDecimal required, BigDecimal available) {
        super("Insufficient balance.\n\nRequired:  " + ConsoleFormatter.money(required)
                + "\nAvailable: " + ConsoleFormatter.money(available));
        this.required = required;
        this.available = available;
    }

    public BigDecimal getRequired() { return required; }
    public BigDecimal getAvailable() { return available; }
}
