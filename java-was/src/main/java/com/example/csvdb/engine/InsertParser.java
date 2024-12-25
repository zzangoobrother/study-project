package com.example.csvdb.engine;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertParser implements SQLParser {

    @Override
    public Map<SQLParserKey, Object> parse(String sql) {
        Map<SQLParserKey, Object> result = new HashMap<>();

        result.put(SQLParserKey.COMMAND, "INSERT");

        String tableRegex = "INSERT INTO\\s+([a-zA-Z0-9_]+)\\s*\\(";
        Pattern tablePattern = Pattern.compile(tableRegex);
        Matcher tableMatcher = tablePattern.matcher(sql);
        if (tableMatcher.find()) {
            result.put(SQLParserKey.TABLE, tableMatcher.group(1));
        }

        String columnsRegex = "\\(([a-zA-Z0-9_,\\s]+)\\)\\s+VALUES";
        Pattern columnsPattern = Pattern.compile(columnsRegex);
        Matcher columnsMatcher = columnsPattern.matcher(sql);
        if (columnsMatcher.find()) {
            String columnsGroup = columnsMatcher.group(1);
            List<String> columns = Arrays.stream(columnsGroup.split(","))
                    .map(String::trim)
                    .toList();
            result.put(SQLParserKey.COLUMNS, columns);
        }

        String valuesSection = sql.substring(sql.indexOf("VALUES") + 6).trim();
        List<List<String>> valuesList = new ArrayList<>();
        StringBuilder valueBuilder = new StringBuilder();
        boolean inValue = false;
        for (char ch : valuesSection.toCharArray()) {
            if (ch == '(') {
                inValue = true;
                valueBuilder = new StringBuilder();
            } else if (ch == ')') {
                inValue = false;
                List<String> values = Arrays.stream(valueBuilder.toString().split(","))
                        .map(String::trim)
                        .map(it -> it.replace("'", ""))
                        .toList();
                valuesList.add(values);
            } else if (inValue) {
                valueBuilder.append(ch);
            }
        }

        result.put(SQLParserKey.VALUES, valuesList);

        return result;
    }
}
