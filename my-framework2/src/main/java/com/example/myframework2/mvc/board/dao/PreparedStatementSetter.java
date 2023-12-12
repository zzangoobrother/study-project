package com.example.myframework2.mvc.board.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface PreparedStatementSetter {
    void setValues(PreparedStatement pstmt) throws SQLException;
}
