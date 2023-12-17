package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.model.Answer;
import com.example.myframework2.mvc.board.model.Result;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JsonView;
import com.example.myframework2.mvc.core.mvc.View;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class AddAnswerController implements Controller {
    private static final Logger log = LoggerFactory.getLogger(AddAnswerController.class);

    @Override
    public View execute(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Answer answer = new Answer(request.getParameter("writer"), request.getParameter("contents"), Long.parseLong(request.getParameter("questionId")));
        log.info("answer : {}", answer);

        AnswerDao answerDao = new AnswerDao();
        Answer saveAnswer = answerDao.insert(answer);

        return new JsonView(saveAnswer);
    }
}
