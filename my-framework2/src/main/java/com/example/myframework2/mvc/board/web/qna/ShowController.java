package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.core.web.mvc.AbstractController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShowController extends AbstractController {
    private AnswerDao answerDao;
    private QuestionDao questionDao;

    public ShowController(AnswerDao answerDao, QuestionDao questionDao) {
        this.answerDao = answerDao;
        this.questionDao = questionDao;
    }

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        Long questionId = Long.parseLong(request.getParameter("questionId"));

        return jspView("/qna/show.jsp")
                .addObject("question", questionDao.findById(questionId))
                .addObject("answers", answerDao.findAllByQuestionId(questionId));
    }
}
