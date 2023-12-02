package myjunit.mvc.part1.http;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HttpResponse {
    private Map<String, String> headers = new HashMap<>();

    private DataOutputStream dos;
    public HttpResponse(OutputStream out) {
        dos = new DataOutputStream(out);

    }

    public void addHeader(String name, String param) {
        headers.put(name, param);
    }

    public void forward(String url) {
        try {
            byte[] body = Files.readAllBytes(new File("./my-framework/webapp", url).toPath());

            if (url.endsWith(".css")) {
                headers.put("Content-Type", "text/css");
            } else if (url.endsWith(".js")) {
                headers.put("Content-Type", "application/javascript");
            } else {
                headers.put("Content-Type", "text/html;charset=utf-8");
            }

            headers.put("Content-Length", body.length + "");
            response200Header(body.length);
            responseBody(body);
        } catch (IOException e) {

        }
    }

    private void forwardBody(String body) {
        byte[] contents = body.getBytes();
        headers.put("Content-Type", "text/html;charset=utf-8");
        headers.put("Content-Length",  contents.length + "");
        response200Header(contents.length);
        responseBody(contents);
    }

    private void response200Header(int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            processHeader();
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void responseBody(byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.writeBytes("\r\n");
            dos.flush();
        } catch (IOException e) {

        }
    }

    public void sendRedirect(String redirectUrl) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            processHeader();
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {

        }
    }

    private void processHeader() {
        try {
            Set<String> keys = headers.keySet();
            for (String key : keys) {
                dos.writeBytes(key + ": " + headers.get(key) + " \r\n");
            }
        } catch (IOException e) {

        }
    }
}
