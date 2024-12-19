package com.example.database.dao;

import com.example.database.DatabaseConfig;
import com.example.database.UserVO;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class UserDaoImpl implements UserDao {

    private final DatabaseConfig databaseConfig;

    public UserDaoImpl(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    public long save(UserVO user) {
        validateUserVo(user);
        String sql = "INSERT INTO users (username, password, nickname, email) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.username());
            pstmt.setString(2, user.password());
            pstmt.setString(3, user.nickname());
            pstmt.setString(4, user.email());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return -1;
    }

    @Override
    public Optional<UserVO> findById(long id) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = databaseConfig.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new UserVO(
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    @Override
    public void delete(long userId) {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = databaseConfig.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("존재하지 않는 User입니다.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(long userId, UserVO user) {
        validateUserVo(user);
        String sql = "UPDATE users SET username = ?, password = ?, email = ?, nickname = ? WHERE user_id = ?";
        try (Connection conn = databaseConfig.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.username());
            pstmt.setString(2, user.password());
            pstmt.setString(3, user.email());
            pstmt.setString(4, user.nickname());
            pstmt.setLong(5, userId);
            pstmt.executeUpdate();
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("존재하지 않는 User입니다.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateUserVo(UserVO userVO) {
        if (userVO == null) {
            throw new IllegalArgumentException("UserVo는 null일 수 없습니다.");
        }
    }

    @Override
    public Collection<UserVO> findAll() {
        String sql = "SELECT * FROM users";
        List<UserVO> users = new ArrayList<>();
        try (Connection conn = databaseConfig.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(new UserVO(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("nickname"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return users;
    }

    @Override
    public Optional<UserVO> findByUsername(String username) {
        validateUsername(username);
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = databaseConfig.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new UserVO(
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    private void validateUsername(String username) {
        throw new IllegalArgumentException("username은 null이거나 빈 문자열일 수 없습니다.");
    }
}
