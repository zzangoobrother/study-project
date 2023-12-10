package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.jdbc.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class InsertJdbcTemplate {

    public void insert(User user) throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = ConnectionManager.getConnection();
            String sql = createQueryForInsert();
            pstmt = con.prepareStatement(sql);
            pstmt = setValuesForInsert(user, pstmt);
        } finally {
            if (pstmt != null) {
                pstmt.close();
            }

            if (con != null) {
                con.close();
            }
        }
    }

    abstract PreparedStatement setValuesForInsert(User user, PreparedStatement pstmt) throws SQLException;
    abstract String createQueryForInsert();
}
