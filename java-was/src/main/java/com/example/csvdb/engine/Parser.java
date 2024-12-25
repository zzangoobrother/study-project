package com.example.csvdb.engine;

import java.util.Map;

public class Parser {
    private static final SelectParser selectParser = new SelectParser();
    private static final InsertParser insertParser = new InsertParser();
    private static final CreateParser createParser = new CreateParser();
    private static final DropParser dropParser = new DropParser();

    public static Map<SQLParserKey, Object> parseSQL(String sql) {
        String command = sql.split("\\s+")[0].toUpperCase();

        return switch (command) {
            case "SELECT" -> selectParser.parse(sql);
            case "INSERT" -> insertParser.parse(sql);
            case "CREATE" -> createParser.parse(sql);
            case "DROP" -> dropParser.parse(sql);
            default -> throw new IllegalArgumentException("Invalid SQL command");
        };
    }
}
