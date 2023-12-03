package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.model.User;

public class CreateUserController extends AbstractController {
    @Override
    void doPost(HttpRequest request, HttpResponse response) {
        User user = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
        DataBase.addUser(user);

        response.sendRedirect("/index.html");
    }
}
