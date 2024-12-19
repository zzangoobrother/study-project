package com.example.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DDLExecutor {
    private static final Logger log = LoggerFactory.getLogger(DDLExecutor.class);

    private final DatabaseConfig databaseConfig;

    public DDLExecutor(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public void executeDDL(String ddlFilePath) throws SQLException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ddlFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (line.trim().endsWith(";")) {
                    executeSQL(sb.toString());
                    sb.setLength(0);
                }
            }

            log.debug("DDL file executed successfully");
        } catch (Exception e) {
            throw new SQLException("Failed to execute DDL file", e);
        }
    }

    private void executeSQL(String sql) throws SQLException {
        try (Connection conn = databaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
