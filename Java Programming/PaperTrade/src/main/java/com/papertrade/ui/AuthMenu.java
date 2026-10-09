package com.papertrade.ui;

import com.papertrade.model.User;
import com.papertrade.service.AuthService;

public class AuthMenu {
    private final AuthService authService;
    private final ConsoleInput in;

    public AuthMenu(AuthService authService, ConsoleInput in) {
        this.authService = authService;
        this.in = in;
    }

    /** Returns the registered user, or null if registration failed/was cancelled. */
    public User register() {
        System.out.println("\n----------- REGISTER -----------  (enter 0 as name to go back)");
        String name = in.ask("Name: ");
        if (name.equals("0")) return null;
        String email = in.ask("Email: ");
        String password = in.ask("Password (min 8 chars, letters + digits): ");
        String confirm = in.ask("Confirm Password: ");
        User[] result = new User[1];
        if (in.safely(() -> result[0] = authService.register(name, email, password, confirm))) {
            System.out.println("\nAccount created! You received a virtual balance of ₹10,00,000.");
            System.out.println("(Paper trading only - no real money is involved.)");
            return result[0];
        }
        return null;
    }

    /** Returns the authenticated user, or null. */
    public User login() {
        System.out.println("\n----------- LOGIN -----------  (enter 0 as email to go back)");
        String email = in.ask("Email: ");
        if (email.equals("0")) return null;
        String password = in.ask("Password: ");
        User[] result = new User[1];
        if (in.safely(() -> result[0] = authService.login(email, password))) {
            System.out.println("\nWelcome back, " + result[0].getName());
            return result[0];
        }
        return null;
    }
}
