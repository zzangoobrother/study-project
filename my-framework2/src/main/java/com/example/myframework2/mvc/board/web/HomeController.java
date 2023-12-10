package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.core.db.DataBase;
import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomeController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        request.setAttribute("users", DataBase.findAll());
        return "home.jsp";
    }
}
