package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.Answer;
import com.example.myframework2.mvc.core.jdbc.JdbcTemplate;
import com.example.myframework2.mvc.core.jdbc.KeyHolder;
import com.example.myframework2.mvc.core.jdbc.PreparedStatementCreator;
import com.example.myframework2.mvc.core.jdbc.RowMapper;

import java.sql.*;
import java.util.List;

public class AnswerDao {
    public Answer insert(Answer answer) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "INSERT INTO ANSWERS (writer, contents, createdDate, questionId) VALUES (?, ?, ?, ?)";
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement pstmt = con.prepareStatement(sql);
                pstmt.setString(1, answer.getWriter());
                pstmt.setString(2, answer.getContents());
                pstmt.setTimestamp(3, new Timestamp(answer.getTimeFromCreateDate()));
                pstmt.setLong(4, answer.getQuestionId());
                return pstmt;
            }
        };

        KeyHolder keyHolder = new KeyHolder();
        jdbcTemplate.update(psc, keyHolder);
        return findById(keyHolder.getId());
    }

    public Answer findById(long answerId) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "SELECT answerId, writer, contents, createdDate, questionId FROM ANSWERS WHERE answerId = ?";

        RowMapper<Answer> rm = new RowMapper<Answer>() {
            @Override
            public Answer mapRow(ResultSet rs) throws SQLException {
                return new Answer(rs.getLong("answerId"), rs.getString("writer"), rs.getString("contents"),
                        rs.getTimestamp("createdDate"), rs.getLong("questionId"));
            }
        };

        return jdbcTemplate.queryForObject(sql, rm, answerId);
    }

    public List<Answer> findAllByQuestionId(Long questionId) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "SELECT answerId, writer, contents, createdDate FROM ANSWERS WHERE questionId = ? order by answerId desc";

        RowMapper<Answer> rm = new RowMapper<Answer>() {
            @Override
            public Answer mapRow(ResultSet rs) throws SQLException {
                return new Answer(rs.getLong("answerId"), rs.getString("writer"), rs.getString("contents"),
                        rs.getTimestamp("createdDate"), questionId);
            }
        };

        return jdbcTemplate.query(sql, rm, questionId);
    }

    public void delete(long answerId) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        String sql = "DELETE FROM ANSWERS WHERE answerId = ?";
        jdbcTemplate.update(sql, answerId);
    }
}
