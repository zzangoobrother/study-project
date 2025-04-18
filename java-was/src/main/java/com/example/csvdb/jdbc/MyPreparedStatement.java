package com.example.csvdb.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

public abstract class MyPreparedStatement implements PreparedStatement {

    @Override
    public ResultSet executeQuery() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int executeUpdate() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
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
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void clearParameters() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void addBatch() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void close() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getMaxFieldSize() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getMaxRows() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setMaxRows(int max) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getQueryTimeout() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void cancel() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void clearWarnings() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setCursorName(String name) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute(String sql) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getUpdateCount() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean getMoreResults() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getFetchDirection() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getFetchSize() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getResultSetType() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void addBatch(String sql) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void clearBatch() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public Connection getConnection() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean isClosed() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean isPoolable() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public void closeOnCompletion() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {

        throw new UnsupportedOperationException("지원하지 않는 기능입니다.");
    }
}
