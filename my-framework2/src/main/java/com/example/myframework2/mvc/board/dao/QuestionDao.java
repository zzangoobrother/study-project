package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.Question;

import java.util.List;

public interface QuestionDao {
    Question insert(Question question);
    Question findById(Long questionId);
    List<Question> findAll();
    void updateCountOfAnswer(long questionId);
    void update(Question question);
    void delete(long questionId);
}
