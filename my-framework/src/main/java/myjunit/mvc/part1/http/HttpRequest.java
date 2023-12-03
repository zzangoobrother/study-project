package myjunit.mvc.part1.http;

import myjunit.mvc.part1.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HttpRequest {
    private HttpHeaders headers;
    private RequestParams params = new RequestParams();

    private HttpMethod method;
    private String path;

    public HttpRequest(InputStream in) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = createLine(br);

            String[] tokens = line.split(" ");
            method = HttpMethod.valueOf(tokens[0]);

            String[] url = tokens[1].split("\\?");
            path = url[0];

            if (url.length == 2) {
                if (url[1] != null || !url[1].isEmpty()) {
                    params.addQueryString(url[1]);
                }
            }

            headers = processHeaders(br);

            String body = IOUtils.readData(br, headers.getContentLength());
            params.addBody(body);
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

    private HttpHeaders processHeaders(BufferedReader br) throws IOException {
        HttpHeaders httpheaders = new HttpHeaders();
        String line;
        while (!"".equals(line = br.readLine())) {
            httpheaders.add(line);
        }

        return httpheaders;
    }

    public HttpMethod getMethod() {
        return this.method;
    }

    public String getPath() {
        return this.path;
    }

    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    public String getParameter(String name) {
        return params.getParam(name);
    }
}
