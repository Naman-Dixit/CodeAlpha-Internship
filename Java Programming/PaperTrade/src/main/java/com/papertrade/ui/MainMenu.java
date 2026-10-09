package com.papertrade.ui;

import com.papertrade.model.User;
import com.papertrade.util.ConsoleFormatter;
import com.papertrade.util.InputValidator;

public class MainMenu {
    private final ConsoleInput in;
    private final AuthMenu authMenu;
    private final TradingMenu tradingMenu;

    public MainMenu(ConsoleInput in, AuthMenu authMenu, TradingMenu tradingMenu) {
        this.in = in;
        this.authMenu = authMenu;
        this.tradingMenu = tradingMenu;
    }

    public void run() {
        while (true) {
            System.out.println(ConsoleFormatter.title("PAPERTRADE"));
            System.out.println("   Stock Trading Platform (paper trading)\n");
            System.out.println("1. Register\n2. Login\n3. Exit");
            switch (InputValidator.parseMenuChoice(in.ask("\nEnter choice: "))) {
                case 1 -> openDashboard(authMenu.register());
                case 2 -> openDashboard(authMenu.login());
                case 3, 0 -> {
                    System.out.println("\nGoodbye!");
                    return;
                }
                default -> System.out.println("\nInvalid choice. Please enter 1, 2 or 3.");
            }
        }
    }

    private void openDashboard(User user) {
        if (user != null) tradingMenu.run(user);
    }
}
