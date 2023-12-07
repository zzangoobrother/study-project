package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.db.DataBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(value = {"/users/login", "/users/loginForm"})
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RequestDispatcher rd = req.getRequestDispatcher("/user/login.jsp");
        rd.forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String userId = req.getParameter("userId");
        User findUser = DataBase.findUserById(userId);
        if (findUser == null) {
            req.setAttribute("loginFailed", true);
            forward("/user/login.jsp", req, resp);
            return;
        }

        if (!findUser.matchPassword(req.getParameter("password"))) {
            req.setAttribute("loginFailed", true);
            forward("/user/login.jsp", req, resp);
            return;
        }

        HttpSession session = req.getSession();
        session.setAttribute("user", findUser);

        resp.sendRedirect("/");
    }

    private void forward(String url, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RequestDispatcher rd = req.getRequestDispatcher(url);
        rd.forward(req, resp);
    }
}
