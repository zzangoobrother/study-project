package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.http.HttpSession;
import myjunit.mvc.part1.model.User;

import java.util.Collection;

public class ListUserController extends AbstractController {
    @Override
    void doGet(HttpRequest request, HttpResponse response) {
        if (isLogin(request.getSession())) {
            Collection<User> users = DataBase.findAll();
            response.forwardBody(getBody(users));
        } else {
            response.sendRedirect("/user/login.html");
        }
    }

    private static String getBody(Collection<User> users) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1'>");
        for (User user : users) {
            sb.append("<tr>");

            sb.append("<td>");
            sb.append(user.getUserId());
            sb.append("</td>");

            sb.append("<td>");
            sb.append(user.getName());
            sb.append("</td>");

            sb.append("<td>");
            sb.append(user.getEmail());
            sb.append("</td>");

            sb.append("</tr>");
        }
        sb.append("</table>");

        return sb.toString();
    }

    boolean isLogin(HttpSession session) {
        Object value = session.getAttribute("user");
        if (value == null) {
            return false;
        }

        return true;
    }
}
