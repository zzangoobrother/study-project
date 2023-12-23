package com.example.myframework2.mvc.board.dao;

import com.example.myframework2.mvc.board.model.Answer;

import java.util.List;

public interface AnswerDao {
    Answer insert(Answer answer);
    Answer findById(long answerId);
    List<Answer> findAllByQuestionId(Long questionId);
    void delete(long answerId);
}
