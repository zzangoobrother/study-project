package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.db.DataBase;
import myjunit.mvc.part1.model.User;
import myjunit.mvc.part1.util.HttpRequestUtils;
import myjunit.mvc.part1.util.IOUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler extends Thread {
    private Socket connection;

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    public void run() {
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {

            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            String line = br.readLine();
            if (line == null) {
                return;
            }

            String url = HttpRequestUtils.getUrl(line);

            Map<String, String> headers = new HashMap<>();
            while (!"".equals(line)) {
                line = br.readLine();
                String[] headerTokens = line.split(": ");
                if (headerTokens.length != 2) {
                    break;
                }

                headers.put(headerTokens[0], headerTokens[1]);
            }

            if ("/user/create".equals(url)) {
                int contentLength = Integer.parseInt(headers.get("Content-Length"));
                String body = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryString(body);

                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                DataBase.addUser(user);

                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "http://localhost:8080/index.html");
                responseBody(dos, new byte[]{});

                return;
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./my-framework/webapp", url).toPath());
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {

        }
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

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {

        }
    }
}
