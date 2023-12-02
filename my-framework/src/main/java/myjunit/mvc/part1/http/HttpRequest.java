package myjunit.mvc.part1.http;

import myjunit.mvc.part1.util.HttpRequestUtils;
import myjunit.mvc.part1.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> bodys = new HashMap<>();

    private String method;
    private String path;

    public HttpRequest(InputStream in) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = createLine(br);

            String[] tokens = line.split(" ");
            method = tokens[0];

            String[] url = tokens[1].split("\\?");
            path = url[0];

            if (url.length == 2) {
                if (url[1] != null || !url[1].isEmpty()) {
                    bodys.putAll(HttpRequestUtils.parseQueryString(url[1]));
                }
            }

            while (!"".equals(line)) {
                line = br.readLine();
                String[] headerTokens = line.split(": ");
                if (headerTokens.length != 2) {
                    break;
                }

                headers.put(headerTokens[0], headerTokens[1]);
            }

            String body = IOUtils.readData(br, Integer.parseInt(headers.getOrDefault("Content-Length", "0")));
            this.bodys.putAll(HttpRequestUtils.parseQueryString(body));
        } catch (IOException e) {

        }
    }

    private String createLine(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) {
            throw new IllegalStateException();
        }

        return line;
    }

    public String getMethod() {
        return this.method;
    }

    public String getPath() {
        return this.path;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public String getParameter(String name) {
        return bodys.get(name);
    }
}
