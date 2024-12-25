package com.example.csvdb.jdbc;

import com.example.csvdb.engine.Parser;
import com.example.csvdb.engine.SQLParserKey;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
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
            resultSet = CsvExecutor.execute(sqlParserKeyObjectMap);
            return resultSet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        Map<SQLParserKey, Object> sqlParserKeyObjectMap = Parser.parseSQL(compiledSql());
        try {
            resultSet = CsvExecutor.execute(sqlParserKeyObjectMap);
            return 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String compiledSql() {
        Matcher matcher = PARAMETER_PATTERN.matcher(sql);
        StringBuilder compiledSql = new StringBuilder();
        int index = 1;
        while (matcher.find()) {
            Object value = parameters.get(index);
            String replacement = value instanceof String ? "'" + value + "'" : value.toString();
            matcher.appendReplacement(compiledSql, replacement);
            index++;
        }

        matcher.appendTail(compiledSql);
        return compiledSql.toString();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return resultSet;
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        parameters.put(parameterIndex, null);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute() throws SQLException {
        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return null;
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return 0;
    }

    @Override
    public void close() throws SQLException {
        // 리소스 정리 로직
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return null;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }
}
