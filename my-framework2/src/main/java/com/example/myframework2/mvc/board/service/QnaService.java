package com.example.myframework2.mvc.board.service;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.board.model.Answer;
import com.example.myframework2.mvc.board.model.Question;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.Service;

import java.util.List;

@Service
public class QnaService {
    private AnswerDao answerDao;
    private QuestionDao questionDao;

    @Inject
    public QnaService(AnswerDao answerDao, QuestionDao questionDao) {
        this.answerDao = answerDao;
        this.questionDao = questionDao;
    }

    public void deleteQuestion(long questionId, User user) {
        Question question = questionDao.findById(questionId);
        if (question == null) {
            throw new IllegalStateException();
        }

        if (!question.isSameUser(user)) {
            throw new IllegalStateException();
        }

        List<Answer> answers = answerDao.findAllByQuestionId(questionId);
        if (answers.isEmpty()) {
            questionDao.delete(questionId);
            return;
        }

        boolean canDelete = true;
        for (Answer answer : answers) {
            String writer = question.getWriter();
            if (!writer.equals(answer.getWriter())) {
                canDelete = false;
                break;
            }
        }

        if (!canDelete) {
            throw new IllegalStateException();
        }

        questionDao.delete(questionId);
    }

    public Question findById(long questionId) {
        return questionDao.findById(questionId);
    }

    public List<Answer> findAllByQuestionId(long questionId) {
        return answerDao.findAllByQuestionId(questionId);
    }
}
