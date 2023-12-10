package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

public class UpdateUserController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        String userId = request.getParameter("userId");

        UserDao userDao = new UserDao();
        User user;
        try {
            user = userDao.findByUserId(userId);
            if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
                throw new IllegalStateException();
            }

            if (user == null) {
                throw new IllegalStateException();
            }

            User updateUser = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
            user.update(updateUser);
            userDao.update(user);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "redirect:/";
    }
}
