package com.example.csvdb.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectParser implements SQLParser {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+(.*?)\\s+FROM\\s+([^\\s]+)(?:\\s+(\\w+))?(?:\\s+LEFT JOIN\\s+([^\\s]+)(?:\\s+(\\w+))?\\s+ON\\s+([^\\s]+)\\s*=\\s*([^\\s]+))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public Map<SQLParserKey, Object> parse(String sql) {
        sql = sql.trim();
        if (!sql.endsWith(";")) {
            throw new IllegalArgumentException("SQL은 세미콜론(;)으로 끝나야합니다.");
        }

        sql = sql.substring(0, sql.length() - 1).trim();

        String[] parts = sql.split("(?i)\\s+WHERE\\s+", 2);
        Map<SQLParserKey, Object> parsedResult = new HashMap<>();
        Matcher matcher = SELECT_PATTERN.matcher(parts[0]);
        parsedResult.put(SQLParserKey.COMMAND, "SELECT");
        if (matcher.find()) {
            String[] split = matcher.group(1).split("\\s*,\\s*");
            for (int i = 0; i < split.length; i++) {
                split[i] = split[i].trim();
            }
            parsedResult.put(SQLParserKey.COLUMNS, Arrays.asList(split));
            parsedResult.put(SQLParserKey.DRIVING_TABLE, matcher.group(2));
            if (matcher.group(3) != null && !matcher.group(3).equalsIgnoreCase("WHERE")) {
                parsedResult.put(SQLParserKey.DRIVING_TABLE_ALIAS, matcher.group(3));
            }

            if (matcher.group(4) != null) {
                parsedResult.put(SQLParserKey.DRIVEN_TABLE, matcher.group(4));
                if (matcher.group(5) != null) {
                    parsedResult.put(SQLParserKey.DRIVEN_TABLE_ALIAS, matcher.group(5));
                }
                parsedResult.put(SQLParserKey.JOIN_CONDITION_LEFT, matcher.group(6));
                parsedResult.put(SQLParserKey.JOIN_CONDITION_RIGHT, matcher.group(7));
            }
        }

        if (parts.length > 1) {
            parsedResult.put(SQLParserKey.WHERE, parts[1].trim());
        }

        return parsedResult;
    }
}
