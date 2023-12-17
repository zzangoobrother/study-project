package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.mvc.AbstractController;
import com.example.myframework2.mvc.core.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginController extends AbstractController {
    private UserDao userDao = UserDao.getInstance();

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User findUser = userDao.findByUserId(userId);

        if (findUser == null) {
            return jspView("/user/login.jsp");
        }

        if (!findUser.matchPassword(request.getParameter("password"))) {
            return jspView("/user/login.jsp");
        }

        HttpSession session = request.getSession();
        session.setAttribute("user", findUser);

        return jspView("redirect:/");
    }
}