package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.model.User;
import myjunit.mvc.part1.util.HttpRequestUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
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

            String url = request.getPath();

            if ("/user/create".equals(url)) {
                User user = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
                DataBase.addUser(user);

                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");

                return;
            } else if ("/user/login".equals(url)) {
                User user = DataBase.findUserById(request.getParameter("userId"));
                if (user != null) {
                    if (user.getPassword().equals(request.getParameter("password"))) {
                        DataOutputStream dos = new DataOutputStream(out);
                        response302LoginSuccessHeader(dos, "http://localhost:8080/index.html", "logined=true");
                    } else {
                        url = "/user/login_failed.html";
                    }
                } else {
                    url = "/user/login_failed.html";
                }
            } else if ("/user/list".equals(url)) {
                String cookie = request.getHeader("Cookie");
                Map<String, String> cookieParams = HttpRequestUtils.parseCookies(cookie);

                DataOutputStream dos = new DataOutputStream(out);
                if (Boolean.parseBoolean(cookieParams.get("logined"))) {
                    Collection<User> users = DataBase.findAll();
                    byte[] body = getBody(users);
                    response200Header(dos, body.length);
                    responseBody(dos, body);
                } else {
                    response302Header(dos, "/user/login.html");
                }
            } else if (url.endsWith(".css")) {
                DataOutputStream dos = new DataOutputStream(out);
                responseCss(dos, url);

                return;
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./my-framework/webapp", url).toPath());
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {

        }
    }

    private static byte[] getBody(Collection<User> users) {
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

        return sb.toString().getBytes();
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void response302Header(DataOutputStream dos, String redirectUrl) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void response302LoginSuccessHeader(DataOutputStream dos, String redirectUrl, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void responseCss(DataOutputStream dos, String url) throws IOException {
        byte[] body = Files.readAllBytes(new File("./my-framework/webapp", url).toPath());
        response200CssHeader(dos, body.length);
        responseBody(dos, body);
    }

    private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {

        }
    }
}
