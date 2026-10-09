package com.papertrade.util;

import com.papertrade.exception.ValidationException;

import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern SYMBOL = Pattern.compile("^[A-Z]{1,10}$");

    private InputValidator() { }

    public static String validateName(String name) throws ValidationException {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new ValidationException("Name must not be empty.");
        if (n.length() > 100) throw new ValidationException("Name must be at most 100 characters.");
        return n;
    }

    /** Returns the normalised (trimmed, lower-case) email. */
    public static String validateEmail(String email) throws ValidationException {
        String e = email == null ? "" : email.trim().toLowerCase();
        if (e.length() > 150 || !EMAIL.matcher(e).matches()) {
            throw new ValidationException("Please enter a valid email address.");
        }
        return e;
    }

    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new ValidationException("Password must contain at least one letter and one digit.");
        }
    }

    public static int parsePositiveInt(String raw, String fieldName) throws ValidationException {
        try {
            int v = Integer.parseInt(raw == null ? "" : raw.trim());
            if (v <= 0) throw new ValidationException(fieldName + " must be a positive whole number.");
            return v;
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a positive whole number.");
        }
    }

    public static String normalizeSymbol(String raw) throws ValidationException {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        if (!SYMBOL.matcher(s).matches()) {
            throw new ValidationException("Please enter a valid stock symbol (letters only, e.g. AAPL).");
        }
        return s;
    }

    /** Parses a menu choice; returns -1 for anything that is not a whole number. */
    public static int parseMenuChoice(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
