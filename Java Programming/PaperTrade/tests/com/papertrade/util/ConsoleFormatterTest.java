package com.papertrade.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConsoleFormatterTest {
    @Test
    void indianGrouping() {
        assertEquals("₹10,00,000.00", ConsoleFormatter.money(new BigDecimal("1000000")));
        assertEquals("₹6,20,000.50", ConsoleFormatter.money(new BigDecimal("620000.5")));
        assertEquals("₹999.00", ConsoleFormatter.money(new BigDecimal("999")));
        assertEquals("₹1,234.57", ConsoleFormatter.money(new BigDecimal("1234.567")));
        assertEquals("-₹12,345.00", ConsoleFormatter.money(new BigDecimal("-12345")));
        assertEquals("₹0.00", ConsoleFormatter.money(BigDecimal.ZERO));
    }
}
