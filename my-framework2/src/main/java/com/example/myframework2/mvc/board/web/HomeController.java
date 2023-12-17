package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomeController implements Controller {
    @Override
    public View execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        QuestionDao questionDao = new QuestionDao();
        request.setAttribute("questions", questionDao.findAll());
        return new JspView("home.jsp");
    }
}
