package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.core.mvc.AbstractController;
import com.example.myframework2.mvc.core.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomeController extends AbstractController {
    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        QuestionDao questionDao = new QuestionDao();
        return jspView("home.jsp")
                .addObject("questions", questionDao.findAll());
    }
}
