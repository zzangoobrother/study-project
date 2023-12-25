package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.model.Result;
import com.example.myframework2.mvc.board.service.QnaService;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.web.mvc.AbstractController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ApiDeleteQuestionController extends AbstractController {
    private QnaService qnaService;

    public ApiDeleteQuestionController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        long questionId = Long.parseLong(request.getParameter("questionId"));
        try {
            qnaService.deleteQuestion(questionId, UserSessionUtils.getUserSession(request.getSession()));
            return jsonView().addObject("result", Result.ok());
        } catch (Exception e) {
            return jsonView().addObject("result", Result.fail(e.getMessage()));
        }
    }
}
