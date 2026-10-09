package com.papertrade.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioSnapshot(LocalDateTime timestamp, BigDecimal cashBalance,
                                BigDecimal stockValue, BigDecimal totalValue) { }
