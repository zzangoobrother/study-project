package com.example.myframework2.mvc.board.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface RowMapper {
    Object mapRow(ResultSet rs) throws SQLException;
}
