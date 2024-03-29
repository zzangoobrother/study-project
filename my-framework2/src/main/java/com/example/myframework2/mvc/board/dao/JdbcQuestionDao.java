package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.Question;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.Repository;
import com.example.myframework2.mvc.core.jdbc.*;

import java.sql.*;
import java.util.List;

@Repository
public class JdbcQuestionDao implements QuestionDao {
    private JdbcTemplate jdbcTemplate;

    @Inject
    public JdbcQuestionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Question insert(Question question) {
        String sql = "INSERT INTO QUESTIONS (writer, title, contents, createdDate) VALUES (?, ?, ?, ?)";

        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement pstmt = con.prepareStatement(sql);
                pstmt.setString(1, question.getWriter());
                pstmt.setString(2, question.getTitle());
                pstmt.setString(3, question.getContents());
                pstmt.setTimestamp(4, new Timestamp(question.getTimeFromCreateDate()));

                return pstmt;
            }
        };

        KeyHolder keyHolder = new KeyHolder();
        jdbcTemplate.update(psc, keyHolder);
        return findById(keyHolder.getId());
    }

    @Override
    public Question findById(Long questionId) {
        String sql = "SELECT questionId, writer, title, contents, createdDate, countOfAnswer FROM QUESTIONS WHERE questionId = ?";

        RowMapper<Question> rm = new RowMapper<Question>() {
            @Override
            public Question mapRow(ResultSet rs) throws SQLException {
                return new Question(rs.getLong("questionId"), rs.getString("writer"), rs.getString("title"),
                        rs.getString("contents"), rs.getTimestamp("createdDate"), rs.getInt("countOfAnswer"));
            }
        };

        return jdbcTemplate.queryForObject(sql, rm, questionId);
    }

    @Override
    public List<Question> findAll() {
        String sql = "SELECT questionId, writer, title, createdDate, countOfAnswer FROM QUESTIONS order by questionId desc";

        RowMapper<Question> rm = new RowMapper<Question>() {
            @Override
            public Question mapRow(ResultSet rs) throws SQLException {
                return new Question(rs.getLong("questionId"), rs.getString("writer"), rs.getString("title"), null,
                        rs.getTimestamp("createdDate"), rs.getInt("countOfAnswer"));
            }
        };

        return jdbcTemplate.query(sql, rm);
    }

    @Override
    public void updateCountOfAnswer(long questionId) {
        String sql = "UPDATE QUESTIONS set countOfAnswer = countOfAnswer + 1 WHERE questionId = ?";
        jdbcTemplate.update(sql, questionId);
    }

    @Override
    public void update(Question question) {
        String sql = "UPDATE QUESTIONS set title = ?, contents = ? WHERE questionId = ?";
        jdbcTemplate.update(sql, question.getTitle(), question.getContents(), question.getQuestionId());
    }

    @Override
    public void delete(long questionId) {
        String sql = "DELETE FROM QUESTIONS WHERE questionId = ?";
        jdbcTemplate.update(sql, questionId);
    }
}
