package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UpdateUserFormController implements Controller {
    @Override
    public View execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        UserDao userDao = new UserDao();
        User user = userDao.findByUserId(userId);

        if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
            throw new IllegalStateException();
        }

        request.setAttribute("user", user);
        return new JspView("/user/updateForm.jsp");
    }
}
