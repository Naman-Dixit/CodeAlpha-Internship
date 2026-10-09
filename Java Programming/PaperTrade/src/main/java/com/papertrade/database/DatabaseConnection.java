package com.papertrade.database;

import com.papertrade.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens JDBC connections using credentials from config.properties (never hard-coded). */
public final class DatabaseConnection {
    private static final String CONFIG_FILE = "config.properties";
    private static volatile Properties props;

    private DatabaseConnection() { }

    public static Connection getConnection() {
        Properties p = config();
        try {
            return DriverManager.getConnection(
                    require(p, "db.url"), require(p, "db.username"), p.getProperty("db.password", ""));
        } catch (SQLException e) {
            throw new DatabaseException("Could not connect to the database. Check that MySQL is running "
                    + "and that db.url / db.username / db.password in config.properties are correct.", e);
        }
    }

    public static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(config().getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new DatabaseException("Missing '" + key + "' in " + CONFIG_FILE, null);
        }
        return v.trim();
    }

    private static Properties config() {
        Properties local = props;
        if (local == null) {
            synchronized (DatabaseConnection.class) {
                if (props == null) props = load();
                local = props;
            }
        }
        return local;
    }

    private static Properties load() {
        Properties p = new Properties();
        Path file = Path.of(CONFIG_FILE);
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            } else {
                try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (in == null) {
                        throw new DatabaseException("config.properties not found. Copy "
                                + "config.example.properties to config.properties and edit it.", null);
                    }
                    p.load(in);
                }
            }
        } catch (IOException e) {
            throw new DatabaseException("Could not read " + CONFIG_FILE, e);
        }
        return p;
    }
}
