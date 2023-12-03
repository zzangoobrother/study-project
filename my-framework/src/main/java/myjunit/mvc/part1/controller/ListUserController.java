package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.model.User;
import myjunit.mvc.part1.util.HttpRequestUtils;

import java.util.Collection;
import java.util.Map;

public class ListUserController extends AbstractController {
    @Override
    void doGet(HttpRequest request, HttpResponse response) {
        String cookie = request.getHeader("Cookie");

        if (isLogin(cookie)) {
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

    boolean isLogin(String cookie) {
        Map<String, String> cookies = HttpRequestUtils.parseCookies(cookie);
        String value = cookies.get("logined");
        if (value == null) {
            return false;
        }

        return Boolean.parseBoolean(value);
    }
}
