package com.example.processor;

import com.example.http.HttpRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpRequestBuilder {

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

        Map<String, String> headers = new HashMap<>();
        while (!(line = br.readLine()).isEmpty()) {
            st = new StringTokenizer(line, ": ");
            String key = st.nextToken();
            String value = st.nextToken();
            headers.put(key, value);
        }

        int contentLength = headers.containsKey("Content-Length") ? Integer.parseInt(headers.get("Content-Length")) : 0;
        char[] body = new char[contentLength];
        br.read(body);

        String bodyString = String.valueOf(body);
        if(headers.containsKey("Content-Type") && headers.get("Content-Type").equals("application/x-www-form-urlencoded")) {
            try {
                bodyString = URLDecoder.decode(bodyString, "UTF-8");
            } catch (Exception e) {
                throw new IllegalArgumentException("인코딩 에러가 발생했습니다.");
            }
        }

        return HttpRequest.builder()
                .method(method)
                .path(path)
                .version(version)
                .headers(headers)
                .body(bodyString)
                .build();
    }
}
