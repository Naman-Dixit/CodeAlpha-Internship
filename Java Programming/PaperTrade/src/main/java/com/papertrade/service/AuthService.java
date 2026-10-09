package com.papertrade.service;

import com.papertrade.dao.UserDAO;
import com.papertrade.database.DatabaseConnection;
import com.papertrade.exception.DatabaseException;
import com.papertrade.exception.PaperTradeException;
import com.papertrade.exception.UserNotFoundException;
import com.papertrade.exception.ValidationException;
import com.papertrade.model.User;
import com.papertrade.util.InputValidator;
import com.papertrade.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class AuthService {
    private static final String DUPLICATE = "An account with this email already exists.";
    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) { this.userDAO = userDAO; }

    public User register(String name, String email, String password, String confirm) throws PaperTradeException {
        String cleanName = InputValidator.validateName(name);
        String cleanEmail = InputValidator.validateEmail(email);
        InputValidator.validatePassword(password);
        if (!password.equals(confirm)) throw new ValidationException("Passwords do not match.");

        try (Connection c = DatabaseConnection.getConnection()) {
            if (userDAO.findByEmail(c, cleanEmail).isPresent()) throw new ValidationException(DUPLICATE);
            User user = new User(0, cleanName, cleanEmail, PasswordUtil.hash(password), User.INITIAL_BALANCE, null);
            int id = userDAO.insert(c, user);
            return userDAO.findById(c, id).orElseThrow(() -> new UserNotFoundException("User not found."));
        } catch (SQLIntegrityConstraintViolationException e) { // lost a race on the UNIQUE(email) index
            throw new ValidationException(DUPLICATE);
        } catch (SQLException e) {
            throw new DatabaseException("Registration failed due to a database error.", e);
        }
    }

    /** Same message for unknown email and wrong password, so accounts cannot be enumerated. */
    public User login(String email, String password) throws PaperTradeException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        try (Connection c = DatabaseConnection.getConnection()) {
            User user = userDAO.findByEmail(c, cleanEmail)
                    .orElseThrow(() -> new UserNotFoundException("Invalid email or password."));
            if (password == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
                throw new UserNotFoundException("Invalid email or password.");
            }
            return user;
        } catch (SQLException e) {
            throw new DatabaseException("Login failed due to a database error.", e);
        }
    }

    public User getUser(int id) throws PaperTradeException {
        try (Connection c = DatabaseConnection.getConnection()) {
            return userDAO.findById(c, id).orElseThrow(() -> new UserNotFoundException("User not found."));
        } catch (SQLException e) {
            throw new DatabaseException("Could not load account.", e);
        }
    }
}
