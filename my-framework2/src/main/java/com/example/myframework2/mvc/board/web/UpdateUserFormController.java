package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

public class UpdateUserFormController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        String userId = request.getParameter("userId");

        User user;
        try {
            UserDao userDao = new UserDao();
            user = userDao.findByUserId(userId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
            throw new IllegalStateException();
        }

        request.setAttribute("user", user);
        return "/user/updateForm.jsp";
    }
}
