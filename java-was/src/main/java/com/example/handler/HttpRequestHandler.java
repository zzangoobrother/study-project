package com.example.handler;

import com.example.http.HttpRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.StringTokenizer;

public class HttpRequestHandler {

    public HttpRequest parseRequest(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line = br.readLine();
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("Request line is null");
        }

        StringTokenizer st = new StringTokenizer(line);
        String method = st.nextToken();
        String path = st.nextToken();
        String version = st.nextToken();

        HashMap<String, String> headers = new HashMap<>();
        while (!(line = br.readLine()).isEmpty()) {
            st = new StringTokenizer(line, ": ");
            String key = st.nextToken();
            String value = st.nextToken();
            headers.put(key, value);
        }

        int contentLength = headers.containsKey("Content-Length") ? Integer.parseInt(headers.get("Content-Length")) : 0;
        char[] body = new char[contentLength];
        br.read(body);

        return HttpRequest.builder()
                .method(method)
                .path(path)
                .version(version)
                .headers(headers)
                .body(String.valueOf(body))
                .build();
    }
}
