package com.papertrade.exception;

/** Unchecked wrapper for infrastructure (JDBC/config) failures. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}
