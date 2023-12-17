package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.mvc.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginController extends AbstractController {
    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        UserDao userDao = new UserDao();
        User findUser = userDao.findByUserId(userId);

        if (findUser == null) {
            return jspView("/user/login.jsp");
        }

        if (!findUser.matchPassword(request.getParameter("password"))) {
            return jspView("/user/login.jsp");
        }

        HttpSession session = request.getSession();

        return jspView("redirect:/")
                .addObject("user", findUser);
    }
}
