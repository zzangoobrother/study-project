package com.example.application.database.dao;

import com.example.application.database.DatabaseConfig;
import com.example.application.database.PostListVO;
import com.example.application.database.PostVO;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class PostDaoImpl implements PostDao {

    private final DatabaseConfig databaseConfig;

    public PostDaoImpl(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    public long save(PostVO data) {
        String sql = "INSERT INTO posts (user_id, content, image_path) VALUES (?, ?, ?)";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, data.userId());
            pstmt.setString(2, data.content());
            pstmt.setString(3, data.imagePath());
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
    public Optional<PostVO> findById(long id) {
        String sql = "SELECT * FROM posts WHERE post_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PostVO(
                            rs.getLong("post_id"),
                            rs.getLong("user_id"),
                            rs.getString("content"),
                            rs.getString("image_path")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM posts WHERE post_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(long id, PostVO data) {
        String sql = "UPDATE posts SET user_id = ?, content = ?, image_path = ? WHERE post_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, data.userId());
            pstmt.setString(2, data.content());
            pstmt.setString(3, data.imagePath());
            pstmt.setLong(4, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<PostVO> findAll() {
        String sql = "SELECT * FROM posts";
        Collection<PostVO> posts = new ArrayList<>();
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                posts.add(new PostVO(
                        rs.getLong("post_id"),
                        rs.getLong("user_id"),
                        rs.getString("content"),
                        rs.getString("image_path")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return posts;
    }

    @Override
    public List<PostListVO> findAllJoinFetch() {
        String sql = "SELECT p.post_id, p.user_id, p.content, p.image_path, u.nickname " +
                "FROM posts p " +
                "LEFT JOIN users u ON p.user_id = u.user_id";
        List<PostListVO> posts = new ArrayList<>();
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                posts.add(new PostListVO(
                        rs.getLong("post_id"),
                        rs.getLong("user_id"),
                        rs.getString("nickname"),
                        rs.getString("content"),
                        rs.getString("image_path")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return posts;
    }
}
