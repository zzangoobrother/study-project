package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

public class ListUserController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return "redirect:/users/loginForm";
        }

        UserDao userDao = new UserDao();
        try {
            request.setAttribute("users", userDao.findAll());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "/user/list.jsp";
    }
}
