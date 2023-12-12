package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.core.jdbc.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {

    public void update(String sql, PreparedStatementSetter pss) throws RuntimeException {
        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pss.setValues(pstmt);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException();
        }
    }

    public List<Object> query(String sql, PreparedStatementSetter pss, RowMapper rm) {
        ResultSet rs = null;
        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pss.setValues(pstmt);


            rs = pstmt.executeQuery();

            List<Object> result = new ArrayList<>();
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

    public Object queryForObject(String sql, PreparedStatementSetter pss, RowMapper rm) {
        List<Object> result = query(sql, pss, rm);
        if (result.isEmpty()) {
            return null;
        }

        return result.get(0);
    }
}
