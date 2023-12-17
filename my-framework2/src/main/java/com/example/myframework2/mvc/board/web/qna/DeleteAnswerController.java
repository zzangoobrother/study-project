package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.model.Result;
import com.example.myframework2.mvc.core.mvc.AbstractController;
import com.example.myframework2.mvc.core.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DeleteAnswerController extends AbstractController {
    private AnswerDao answerDao = AnswerDao.getInstance();

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        long answerId = Long.parseLong(request.getParameter("answerId"));

        ModelAndView modelAndView = jsonView();
        try {
            answerDao.delete(answerId);
            modelAndView.addObject("result", Result.ok());
        } catch (Exception e) {
            modelAndView.addObject("result", Result.fail(e.getMessage()));
        }

        return modelAndView;
    }
}
