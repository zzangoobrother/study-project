package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginController implements Controller {
    @Override
    public View execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        UserDao userDao = new UserDao();
        User findUser = userDao.findByUserId(userId);

        if (findUser == null) {
            return new JspView("/user/login.jsp");
        }

        if (!findUser.matchPassword(request.getParameter("password"))) {
            return new JspView("/user/login.jsp");
        }

        HttpSession session = request.getSession();
        session.setAttribute("user", findUser);

        return new JspView("redirect:/");
    }
}
