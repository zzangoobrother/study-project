package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ListUserController implements Controller {
    @Override
    public View execute(HttpServletRequest request, HttpServletResponse response) {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return new JspView("redirect:/users/loginForm");
        }

        UserDao userDao = new UserDao();
        request.setAttribute("users", userDao.findAll());
        return new JspView("/user/list.jsp");
    }
}
