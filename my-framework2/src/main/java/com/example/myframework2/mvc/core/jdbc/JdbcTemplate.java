package com.example.myframework2.mvc.core.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {
    private DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        super();
        this.dataSource = dataSource;
    }

    public void update(String sql, PreparedStatementSetter pss) throws RuntimeException {
        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pss.setValues(pstmt);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException();
        }
    }

    public void update(String sql, Object... parameters) {
        update(sql, createPreparedStatementSetter(parameters));
    }

    public void update(PreparedStatementCreator psc, KeyHolder keyHolder) {
        try (Connection con = ConnectionManager.getConnection()) {
            PreparedStatement ps = psc.createPreparedStatement(con);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                keyHolder.setId(rs.getLong(1));
            }

            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException();
        }
    }

    public <T> List<T> query(String sql, PreparedStatementSetter pss, RowMapper<T> rm) {
        ResultSet rs = null;
        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pss.setValues(pstmt);


            rs = pstmt.executeQuery();

            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rm.mapRow(rs));
            }

            return result;
        } catch (SQLException e) {
            throw new RuntimeException();
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rm, Object... parameters) {
        return query(sql, createPreparedStatementSetter(parameters), rm);
    }

    public <T> T queryForObject(String sql, PreparedStatementSetter pss, RowMapper<T> rm) {
        List<T> result = query(sql, pss, rm);
        if (result.isEmpty()) {
            return null;
        }

        return result.get(0);
    }

    public <T> T queryForObject(String sql, RowMapper<T> rm, Object... parameters) {
        return queryForObject(sql, createPreparedStatementSetter(parameters), rm);
    }

    private PreparedStatementSetter createPreparedStatementSetter(Object... parameters) {
        return new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement pstmt) throws SQLException {
                for (int i = 0; i < parameters.length; i++) {
                    pstmt.setObject(i + 1, parameters[i]);
                }
            }
        };
    }
}
