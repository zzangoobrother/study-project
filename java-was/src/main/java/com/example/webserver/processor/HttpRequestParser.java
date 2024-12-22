package com.example.webserver.processor;

import com.example.webserver.http.HttpRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestParser {

    public HttpRequest parseRequest(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line = br.readLine();
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("Request line is null");
        }

        String[] requestLineParts = line.split(" ");
        if (requestLineParts.length < 3) {
            throw new IllegalArgumentException("Invalid request line : " + line);
        }

        String method = requestLineParts[0];
        String path = requestLineParts[1];
        String version = requestLineParts[2];

        Map<String, List<String>> headers = new HashMap<>();
        while (!(line = br.readLine()).isEmpty()) {
            int colonIndex = line.indexOf(":");
            if (colonIndex == -1) {
                throw new IllegalArgumentException("Invalid header line : " + line);
            }

            String key = line.substring(0, colonIndex).trim();
            String valuesString = line.substring(colonIndex + 1).trim();
            String[] values = valuesString.split(";");

            List<String> subList = headers.computeIfAbsent(key, k -> new ArrayList<>());
            for (String value : values) {
                subList.add(value.trim());
            }
        }

        int contentsLength = headers.containsKey("Content-Length") ? Integer.parseInt(headers.get("Content-Length").get(0)) : 0;
        char[] body = new char[contentsLength];
        br.read(body);

        String bodyString = String.valueOf(body);
        if (headers.containsKey("Content-Type") && headers.get("Content-Type").get(0).equals("application/x-www-form-urlencoded")) {
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
