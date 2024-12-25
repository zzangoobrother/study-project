package com.example.csvdb;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class CsvDriver implements Driver {

    private static boolean registered;
    private static final CsvDriver INSTANCE = new CsvDriver();

    static {
        load();
    }

    private static synchronized Driver load() {
        try {
            if (!registered) {
                registered = true;
                DriverManager.registerDriver(INSTANCE);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Can't register driver!");
        }

        return INSTANCE;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        String folderPath = url.substring("jdbc:csvdb:".length());
        return new CsvConnection(folderPath);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return false;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
