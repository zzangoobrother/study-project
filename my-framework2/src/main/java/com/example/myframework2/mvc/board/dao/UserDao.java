package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UserDao {
    public void insert(User user) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "INSERT INTO USERS VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, user.getUserId(), user.getPassword(), user.getName(), user.getEmail());
    }

    public void update(User user) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "UPDATE USERS set password=?, name=?, email=? where userid=?";
        jdbcTemplate.update(sql, user.getPassword(), user.getName(), user.getEmail(), user.getUserId());
    }

    public User findByUserId(String userId) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();

        RowMapper<User> rm = new RowMapper<User>() {
            @Override
            public User mapRow(ResultSet rs) throws SQLException {
                return new User(rs.getString("userId"), rs.getString("password"), rs.getString("name"),
                        rs.getString("email"));
            }
        };

        String sql = "SELECT userId, password, name, email FROM USERS WHERE userid=?";
        return jdbcTemplate.queryForObject(sql, rm, userId);
    }

    public List<User> findAll() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();

        RowMapper<User> rm = new RowMapper<User>() {
            @Override
            public User mapRow(ResultSet rs) throws SQLException {
                return new User(rs.getString("userId"), rs.getString("password"), rs.getString("name"),
                        rs.getString("email"));
            }
        };

        return jdbcTemplate.query("SELECT userId, password, name, email FROM USERS", rm);
    }
}
