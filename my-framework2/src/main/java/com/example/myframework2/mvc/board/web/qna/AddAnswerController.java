package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.board.model.Answer;
import com.example.myframework2.mvc.board.model.Result;
import com.example.myframework2.mvc.core.web.mvc.AbstractController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AddAnswerController extends AbstractController {
    private static final Logger log = LoggerFactory.getLogger(AddAnswerController.class);
    private AnswerDao answerDao;
    private QuestionDao questionDao;

    public AddAnswerController(AnswerDao answerDao, QuestionDao questionDao) {
        this.answerDao = answerDao;
        this.questionDao = questionDao;
    }

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long questionId = Long.parseLong(request.getParameter("questionId"));
        Answer answer = new Answer(request.getParameter("writer"), request.getParameter("contents"), questionId);
        log.info("answer : {}", answer);

        Answer saveAnswer = answerDao.insert(answer);

        questionDao.updateCountOfAnswer(questionId);

        return jsonView()
                .addObject("answer", saveAnswer)
                .addObject("result", Result.ok());
    }
}
