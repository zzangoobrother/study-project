package com.example.csvdb.jdbc;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class CsvResultSet extends MyResultSet {
    private int currentIndex = -1;

    private final List<Map<String, String>> data;

    public CsvResultSet(List<Map<String, String>> data) {
        this.data = data;
    }

    @Override
    public boolean next() throws SQLException {
        if (currentIndex + 1 < data.size()) {
            currentIndex++;
            return true;
        }

        return false;
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        Object value = data.get(currentIndex).get(columnLabel);
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        throw new SQLException("String으로 변환할 수 없는 값입니다.");
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        Object value = data.get(currentIndex).get(columnLabel);
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (boolean) value;
        }

        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }

        throw new SQLException("Boolean으로 변환할 수 없는 값입니다.");
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        String columnLabel = (String) data.get(currentIndex).keySet().toArray()[columnIndex - 1];
        return getBoolean(columnLabel);
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        String columnLabel = (String) data.get(currentIndex).keySet().toArray()[columnIndex - 1];
        return getString(columnLabel);
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        Object value = data.get(currentIndex).get(columnLabel);
        if (value == null) {
            return 0;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }

        throw new SQLException("Int로 변환할 수 없는 값입니다.");
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        Object value = data.get(currentIndex).get(columnLabel);
        if (value == null) {
            return 0;
        }

        if (value instanceof  Long) {
            return (Long) value;
        }

        if (value instanceof String) {
            return Long.parseLong((String) value);
        }

        throw new SQLException("Long으로 변환할 수 없는 값입니다.");
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        columnLabel = columnLabel.trim();
        Object value = data.get(currentIndex).get(columnLabel);
        if (value == null) {
            return null;
        }

        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }

        if (value instanceof String) {
            try {
                return Timestamp.valueOf((String) value);
            } catch (IllegalArgumentException e) {
                throw new SQLException("Timestamp로 변환할 수 없는 값입니다.", e);
            }
        }

        throw new SQLException("Timestamp로 변환할 수 없는 값입니다.");
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        String columnLabel = (String) data.get(currentIndex).keySet().toArray()[columnIndex - 1];
        return getInt(columnLabel);
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        Map<String, String> stringStringMap = data.get(currentIndex);
        if (stringStringMap.containsKey("GENERATED_KEY")) {
            return Long.parseLong(stringStringMap.get("GENERATED_KEY"));
        }

        String columnLabel = (String) stringStringMap.keySet().toArray()[columnIndex - 1];
        return getLong(columnLabel);
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        String columnLabel = (String) data.get(currentIndex).keySet().toArray()[columnIndex - 1];
        return getTimestamp(columnLabel);
    }

    @Override
    public boolean wasNull() throws SQLException {
        return data.get(currentIndex) == null;
    }
}
