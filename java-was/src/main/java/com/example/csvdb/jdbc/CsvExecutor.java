package com.example.csvdb.jdbc;

import com.example.csvdb.engine.SQLParserKey;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CsvExecutor {

    private static final ConcurrentHashMap<String, AtomicLong> autoIncrementMap = new ConcurrentHashMap<>();
    private static final Set<String> usernameUniqueSet = new HashSet<>();
    private static final Set<String> emailUniqueSet = new HashSet<>();

    public static ResultSet execute(Map<SQLParserKey, Object> parsed) throws IOException {
        String command = (String) parsed.get(SQLParserKey.COMMAND);

        switch (command) {
            case "SELECT" -> select(parsed);
            case "INSERT" -> getGeneratedKeyAndInsert(parsed);
            case "DROP" -> {
                String tableName = (String) parsed.get(SQLParserKey.TABLE);
                CsvFileManager.dropTable(tableName);
                return null;
            }
            case "CREATE" -> {
                String tableName = (String) parsed.get(SQLParserKey.TABLE);
                List<String> columns = (List<String>) parsed.get(SQLParserKey.COLUMNS);
                CsvFileManager.createTable(tableName, columns);
                return null;
            }
        }

        return null;
    }

    private static ResultSet select(Map<SQLParserKey, Object> parsed) {
        String tableName = (String) parsed.get(SQLParserKey.DRIVING_TABLE);
        String tableAlias = (String) parsed.get(SQLParserKey.DRIVING_TABLE_ALIAS);
        List<String> columns = (List<String>) parsed.get(SQLParserKey.COLUMNS);
        String whereClause = (String) parsed.get(SQLParserKey.WHERE);
        String joinTable = (String) parsed.get(SQLParserKey.DRIVEN_TABLE);
        String joinTableAlias = (String) parsed.get(SQLParserKey.DRIVEN_TABLE_ALIAS);
        String joinConditionLeft = (String) parsed.get(SQLParserKey.JOIN_CONDITION_LEFT);
        String joinConditionRight = (String) parsed.get(SQLParserKey.JOIN_CONDITION_RIGHT);

        List<Map<String, String>> drivingTableData = CsvFileManager.readTable(tableName);

        if (joinTable != null) {
            List<Map<String, String>> joinTableData = CsvFileManager.readTable(joinTable);
            drivingTableData = joinTables(drivingTableData, joinTableData, joinConditionLeft, joinConditionRight, tableAlias, joinTableAlias);
        }

        List<Map<String, String>> results = filterData(drivingTableData, columns, whereClause);

        return new CsvResultSet(results);
    }

    private static List<Map<String, String>> joinTables(List<Map<String, String>> baseTable, List<Map<String, String>> joinTable, String joinConditionLeft, String joinConditionRight, String baseTableAlias, String joinTableAlias) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> base : baseTable) {
            for (Map<String, String> join : joinTable) {
                String baseKey = joinConditionLeft.replace(baseTableAlias + ".", "");
                String baseValue = base.get(baseKey);
                String joinKey = joinConditionRight.replace(joinTableAlias + ".", "");
                String joinValue = join.get(joinKey);

                if (!baseValue.equals(joinValue)) continue;

                Map<String, String> ret = new HashMap<>();
                for (Map.Entry<String, String> entry : base.entrySet()) {
                    ret.put(baseTableAlias + "." + entry.getKey(), entry.getValue());
                }

                for (Map.Entry<String, String> entry : join.entrySet()) {
                    if (entry.getKey().equals(joinKey)) continue;

                    ret.put(joinTableAlias + "." + entry.getKey(), entry.getValue());
                }

                result.add(ret);
            }
        }

        return result;
    }

    private static List<Map<String, String>> filterData(List<Map<String, String>> data, List<String> selectedColumns, String whereClause) {
        if (whereClause == null) {
            return data;
        }

        return data.stream()
                .filter(row -> evaluateWhereClause(row, whereClause))
                .map(row -> {
                    Map<String, String> selectedRow = new LinkedHashMap<>();
                    for (String column : selectedColumns) {
                        selectedRow.put(column, row.get(column));
                    }

                    return selectedRow;
                }).toList();
    }

    private static boolean evaluateWhereClause(Map<String, String> row, String whereClause) {
        String[] parts = whereClause.split(" ");
        if (parts.length != 3) {
            return false;
        }

        String column = parts[0];
        String operator = parts[1];
        String value = parts[2];

        if (!row.containsKey(column)) {
            return false;
        }

        String columnValue = row.get(column);

        return switch (operator) {
            case ">" -> Integer.parseInt(columnValue) > Integer.parseInt(value);
            case "<" -> Integer.parseInt(columnValue) < Integer.parseInt(value);
            case "=" -> columnValue.equals(value);
            case ">=" -> Integer.parseInt(columnValue) >= Integer.parseInt(value);
            case "<=" -> Integer.parseInt(columnValue) <= Integer.parseInt(value);
            default -> false;
        };
    }

    private static ResultSet getGeneratedKeyAndInsert(Map<SQLParserKey, Object> parsed) throws IOException {
        String tableName = (String) parsed.get(SQLParserKey.TABLE);
        List<String> columns = (List<String>) parsed.get(SQLParserKey.COLUMNS);
        List<List<String>> valueList = (List<List<String>>) parsed.get(SQLParserKey.VALUES);

        AtomicLong autoIncrement = autoIncrementMap.computeIfAbsent(tableName, v -> new AtomicLong(0));

        List<String> usernames = new ArrayList<>();
        if (columns.contains("username")) {
            int index = columns.indexOf("username") - 1;
            for (List<String> values : valueList) {
                String username = values.get(index);
                if (usernameUniqueSet.contains(username)) {
                    throw new IllegalArgumentException("사용자 ID는 중복될 수 없습니다.");
                }
                usernames.add(username);
            }
        }

        List<String> emails = new ArrayList<>();

        if (columns.contains("email")) {
            int index = columns.indexOf("email") - 1;
            for (List<String> values : valueList) {
                String email = values.get(index);
                if (emailUniqueSet.contains(email)) {
                    throw new IllegalArgumentException("이메일은 중복될 수 없습니다.");
                }

                emails.add(email);
            }
        }

        emailUniqueSet.addAll(emails);
        usernameUniqueSet.addAll(usernames);

        List<Map<String, String>> resultData = new ArrayList<>();
        valueList.forEach(values -> {
            long generatedKey = autoIncrement.incrementAndGet();

            values.add(0, String.valueOf(generatedKey));

            Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
            values.add(timestamp.toString());
            resultData.add(Map.of("GENERATED_KEY", String.valueOf(generatedKey)));
        });

        CsvFileManager.writeData(tableName, columns, valueList);

        return new CsvResultSet(resultData);
    }
}
