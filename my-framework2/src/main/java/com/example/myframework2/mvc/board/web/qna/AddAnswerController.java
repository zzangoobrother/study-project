package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.model.Answer;
import com.example.myframework2.mvc.core.mvc.Controller;
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
    public String execute(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Answer answer = new Answer(request.getParameter("writer"), request.getParameter("contents"), Long.parseLong(request.getParameter("questionId")));
        log.info("answer : {}", answer);

        AnswerDao answerDao = new AnswerDao();
        Answer saveAnswer = answerDao.insert(answer);

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        ObjectMapper objectMapper = new ObjectMapper();
        out.println(objectMapper.writeValueAsString(saveAnswer));

        return null;
    }
}
