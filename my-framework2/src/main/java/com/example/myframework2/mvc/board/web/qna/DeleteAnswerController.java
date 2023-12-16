package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.model.Result;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

public class DeleteAnswerController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        long answerId = Long.parseLong(request.getParameter("answerId"));
        AnswerDao answerDao = new AnswerDao();

        answerDao.delete(answerId);

        ObjectMapper objectMapper = new ObjectMapper();
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println(objectMapper.writeValueAsString(Result.ok()));

        return null;
    }
}
