package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.controller.Controller;
import myjunit.mvc.part1.http.HttpCookie;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;
import myjunit.mvc.part1.util.HttpRequestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
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

            HttpCookie cookie = request.getCookies();
            if (cookie.getCookie("JSESSIONID") == null) {
                response.addHeader("Set-Cookie", "JSESSIONID=" + UUID.randomUUID());
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
