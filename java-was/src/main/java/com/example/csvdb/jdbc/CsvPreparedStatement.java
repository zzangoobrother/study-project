package com.example.csvdb.jdbc;

import com.example.csvdb.engine.Parser;
import com.example.csvdb.engine.SQLParserKey;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class CsvPreparedStatement extends MyPreparedStatement {

    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\?");

    private final String sql;
    private final Map<Integer, Object> parameters = new HashMap<>();
    private ResultSet resultSet;

    public CsvPreparedStatement(String sql) {
        this.sql = sql;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        Map<SQLParserKey, Object> sqlParserKeyObjectMap = Parser.parseSQL(sql);
        try {
            resultSet = CsvExecutor
        }
    }
}
