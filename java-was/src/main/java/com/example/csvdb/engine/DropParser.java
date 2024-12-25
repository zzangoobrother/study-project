package com.example.csvdb.engine;

import java.util.HashMap;
import java.util.Map;

public class DropParser implements SQLParser {

    private final String DROP_STRING = "DROP TABLE IF EXISTS";

    @Override
    public Map<SQLParserKey, Object> parse(String sql) {
        sql = sql.trim();
        if (!sql.endsWith(";")) {
            throw new IllegalArgumentException("SQL은 세미콜론(;)으로 끝나야합니다.");
        }

        String replacedSql = sql.replace(";", "");
        String table = replacedSql.replace(DROP_STRING, "").trim();

        Map<SQLParserKey, Object> parsedResult = new HashMap<>();
        parsedResult.put(SQLParserKey.COMMAND, "DROP");
        parsedResult.put(SQLParserKey.TABLE, table);

        return parsedResult;
    }
}
