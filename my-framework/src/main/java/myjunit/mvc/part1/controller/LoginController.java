package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.model.User;

public class LoginController extends AbstractController {
    @Override
    void doPost(HttpRequest request, HttpResponse response) {
        User user = DataBase.findUserById(request.getParameter("userId"));
        if (user == null) {
            response.forward("/user/login_failed.html");
        }

        if (user.getPassword().equals(request.getParameter("password"))) {
            response.addHeader("Set-Cookie", "logined=true");
            response.sendRedirect("/index.html");
        } else {
            response.forward("/user/login_failed.html");
        }
    }
}
