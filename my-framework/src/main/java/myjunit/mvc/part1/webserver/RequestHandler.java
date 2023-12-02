package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.model.User;
import myjunit.mvc.part1.util.HttpRequestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;

public class RequestHandler extends Thread {
    private Socket connection;

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    public void run() {
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {

            HttpRequest request = new HttpRequest(in);
            HttpResponse response = new HttpResponse(out);

            String url = request.getPath();

            if ("/user/create".equals(url)) {
                User user = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
                DataBase.addUser(user);

                response.sendRedirect("/index.html");
            } else if ("/user/login".equals(url)) {
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
            } else if ("/user/list".equals(url)) {
                String cookie = request.getHeader("Cookie");
                Map<String, String> cookieParams = HttpRequestUtils.parseCookies(cookie);

                if (Boolean.parseBoolean(cookieParams.get("logined"))) {
                    Collection<User> users = DataBase.findAll();
                    response.forwardBody(getBody(users));
                } else {
                    response.sendRedirect("/user/login.html");
                }
            } else {
                response.forward(url);
            }
        } catch (IOException e) {

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
}
