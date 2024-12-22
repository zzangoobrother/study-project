package com.example.application.database.dao;

import com.example.application.database.DatabaseConfig;
import com.example.application.database.CommentVO;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class CommentDaoImpl implements CommentDao {

    private final DatabaseConfig databaseConfig;

    public CommentDaoImpl(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    public long save(CommentVO data) {
        String sql = "INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, ?)";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, data.postId());
            pstmt.setLong(2, data.userId());
            pstmt.setString(3, data.content());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("comment를 저장하는 데 실패했습니다, no rows affected.");
            }
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
    public Optional<CommentVO> findById(long commentId) {
        String sql = "SELECT * FROM comments WHERE comment_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, commentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CommentVO(
                            rs.getLong("comment_id"),
                            rs.getLong("post_id"),
                            rs.getLong("user_id"),
                            rs.getString("content"),
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
    public void delete(long commentId) {
        String sql = "DELETE FROM comments WHERE comment_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, commentId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("comment를 삭제하는 데 실패했습니다, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(long commentId, CommentVO data) {
        String sql = "UPDATE comments SET post_id = ?, user_id = ?, content = ? WHERE comment_id = ?";
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, data.postId());
            pstmt.setLong(2, data.userId());
            pstmt.setString(3, data.content());
            pstmt.setLong(4, commentId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("comment를 업데이트하는 데 실패했습니다, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<CommentVO> findAll() {
        String sql = "SELECT * FROM comments";
        Collection<CommentVO> comments = new ArrayList<>();
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                comments.add(new CommentVO(
                        rs.getLong("comment_id"),
                        rs.getLong("post_id"),
                        rs.getLong("user_id"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return comments;
    }

    @Override
    public List<CommentVO> findByPostId(Long postId) {
        String sql = "SELECT * FROM comments WHERE post_id = ?";
        List<CommentVO> comments = new ArrayList<>();
        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, postId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    comments.add(new CommentVO(
                            rs.getLong("comment_id"),
                            rs.getLong("post_id"),
                            rs.getLong("user_id"),
                            rs.getString("content"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return comments;
    }
}