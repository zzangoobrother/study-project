package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.http.HttpSession;
import myjunit.mvc.part1.model.User;

public class LoginController extends AbstractController {
    @Override
    void doPost(HttpRequest request, HttpResponse response) {
        User user = DataBase.findUserById(request.getParameter("userId"));
        if (user == null) {
            response.forward("/user/login_failed.html");
        }

        if (user.matchPassword(request.getParameter("password"))) {
            HttpSession session = request.getSession();
            session.setAttribute("user", user);
            response.sendRedirect("/index.html");
        } else {
            response.forward("/user/login_failed.html");
        }
    }
}
