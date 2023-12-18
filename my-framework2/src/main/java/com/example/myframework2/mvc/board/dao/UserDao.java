package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.jdbc.JdbcTemplate;

import java.sql.ResultSet;
import java.util.List;

public class UserDao {
    private JdbcTemplate jdbcTemplate = JdbcTemplate.getInstance();

    private static UserDao userDao = new UserDao();

    private UserDao() {}

    public static UserDao getInstance() {
        return userDao;
    }

    public void insert(User user) {
        String sql = "INSERT INTO USERS VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, user.getUserId(), user.getPassword(), user.getName(), user.getEmail());
    }

    public void update(User user) {
        String sql = "UPDATE USERS set password=?, name=?, email=? where userid=?";
        jdbcTemplate.update(sql, user.getPassword(), user.getName(), user.getEmail(), user.getUserId());
    }

    public User findByUserId(String userId) {
        String sql = "SELECT userId, password, name, email FROM USERS WHERE userid=?";
        return jdbcTemplate.queryForObject(sql,
                (ResultSet rs) -> new User(rs.getString("userId"), rs.getString("password"), rs.getString("name"), rs.getString("email")),
                userId);
    }

    public List<User> findAll() {
        String sql = "SELECT userId, password, name, email FROM USERS";
        return jdbcTemplate.query(sql, (ResultSet rs) -> new User(rs.getString("userId"), rs.getString("password"), rs.getString("name"), rs.getString("email")));
    }
}
