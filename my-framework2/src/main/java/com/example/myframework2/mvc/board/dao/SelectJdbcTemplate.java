package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.core.jdbc.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class SelectJdbcTemplate {

    public List<Object> query(String sql) {
        ResultSet rs = null;
        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            setValues(pstmt);


            rs = pstmt.executeQuery();

            List<Object> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
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

    public Object queryForObject(String sql) {
        List<Object> result = query(sql);
        if (result.isEmpty()) {
            return null;
        }

        return result.get(0);
    }

    abstract PreparedStatement setValues(PreparedStatement pstmt) throws SQLException;
    abstract Object mapRow(ResultSet rs) throws SQLException;
}
