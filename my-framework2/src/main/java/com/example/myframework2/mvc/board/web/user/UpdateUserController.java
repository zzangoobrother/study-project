package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.web.mvc.AbstractController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UpdateUserController extends AbstractController {
    private UserDao userDao;

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User user = userDao.findByUserId(userId);
        if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
            throw new IllegalStateException();
        }

        if (user == null) {
            throw new IllegalStateException();
        }

        User updateUser = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
        user.update(updateUser);
        userDao.update(user);

        return jspView("redirect:/");
    }
}
