package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.db.DataBase;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ListUserController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return "redirect:/users/loginForm";
        }

        request.setAttribute("users", DataBase.findAll());
        return "/user/list.jsp";
    }
}
