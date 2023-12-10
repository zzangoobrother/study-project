package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.db.DataBase;
import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginController implements Controller {
    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        String userId = request.getParameter("userId");
        User findUser = DataBase.findUserById(userId);
        if (findUser == null) {
            return "/user/login.jsp";
        }

        if (!findUser.matchPassword(request.getParameter("password"))) {
            return "/user/login.jsp";
        }

        HttpSession session = request.getSession();
        session.setAttribute("user", findUser);

        return "redirect:/";
    }
}
