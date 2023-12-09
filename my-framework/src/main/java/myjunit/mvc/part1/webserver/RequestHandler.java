package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.controller.Controller;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.http.HttpSessions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

public class RequestHandler extends Thread {
    private Socket connection;

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    public void run() {
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            HttpRequest request = new HttpRequest(in);
            HttpResponse response = new HttpResponse(out);

            if (request.getCookies().getCookie(HttpSessions.SESSION_ID_NAME) == null) {
                response.addHeader("Set-Cookie", HttpSessions.SESSION_ID_NAME + "=" + UUID.randomUUID());
            }

            Controller controller = RequestMapping.getController(request.getPath());
            if (controller == null) {
                String path = request.getPath();
                response.forward(getDefaultPath(path));
            } else {
                controller.service(request, response);
            }
        } catch (IOException e) {

        }
    }

    private String getDefaultPath(String path) {
        if ("/".equals(path)) {
            return "index.html";
        }
        return path;
    }
}
