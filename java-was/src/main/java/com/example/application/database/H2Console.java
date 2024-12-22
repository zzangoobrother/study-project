package com.example.application.database;

import org.h2.tools.Server;

import java.sql.SQLException;

public class H2Console {

    public static void main(DatabaseConfig databaseConfig) {
        try {
            Server webServer = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082");
            webServer.start();

            DDLExecutor ddlExecutor = new DDLExecutor(databaseConfig);
            ddlExecutor.executeDDL("db/ddl.sql");

            Thread.currentThread().join();
        } catch (SQLException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
