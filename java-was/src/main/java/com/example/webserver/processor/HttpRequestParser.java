package com.example.webserver.processor;

import com.example.webserver.exception.BadRequestException;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpVersion;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

public class HttpRequestParser {
    private static final String CHARSET = "UTF-8";

    private static final String HEADER_END = "\r\n\r\n";

    public HttpRequest parseRequest(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();

        boolean headerEnd = false;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            headerBuffer.write(buffer, 0, bytesRead);
            String currentHeaders = headerBuffer.toString(CHARSET);
            if (currentHeaders.contains(HEADER_END)) {
                headerEnd = true;
                break;
            }
        }

        if (!headerEnd) {
            throw new IllegalArgumentException("Invalid HTTP request: no end of headers found");
        }

        byte[] headerBytes = headerBuffer.toByteArray();
        String headers = new String(headerBytes, CHARSET);

        int headerEndIndex = headers.indexOf(HEADER_END) + 4;
        byte[] remainingBody = Arrays.copyOfRange(headerBytes, headerEndIndex, headerBytes.length);

        String[] requestLines = headers.split("\r\n");
        String requestLine = requestLines[0];
        if (requestLine == null || requestLine.isEmpty()) {
            throw new BadRequestException("Request line is null");
        }

        String[] requestLineParts = requestLine.split(" ");
        if (requestLineParts.length < 3) {
            throw new BadRequestException("Invalid request line: " + requestLine);
        }

        String method = requestLineParts[0];
        String path = requestLineParts[1];
        String version = requestLineParts[2].trim();

        HttpVersion httpVersion = HttpVersion.of(version);

        Map<String, List<String>> headerMap = new HashMap<>();
        int i = 1;
        while (i < requestLines.length && !requestLines[i].isEmpty()) {
            int colonIndex = requestLines[i].indexOf(":");
            if (colonIndex == -1) {
                throw new BadRequestException("Invalid header line: " + requestLines[i]);
            }

            String key = requestLines[i].substring(0, colonIndex).trim();
            String valuesString = requestLines[i].substring(colonIndex + 1).trim();
            String[] values = valuesString.split(";");

            List<String> subList = headerMap.computeIfAbsent(key, k -> new ArrayList<>());
            for (String value : values) {
                subList.add(value.trim());
            }
            i++;
        }

        int contentsLength = headerMap.containsKey("Content-Length") ? Integer.parseInt(headerMap.get("Content-Length").get(0)) : 0;
        ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();

        if (remainingBody.length > 0) {
            bodyBuffer.write(remainingBody, 0, Math.min(remainingBody.length, contentsLength));
        }

        int remainingBytes = contentsLength - remainingBody.length;
        while (remainingBytes > 0 && (bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, remainingBytes))) != -1) {
            bodyBuffer.write(buffer, 0, bytesRead);
            remainingBytes -= bytesRead;
        }

        byte[] body = bodyBuffer.toByteArray();

        if (headerMap.containsKey("Content-Type") && headerMap.get("Content-Type").get(0).equals("application/x-www-form-urlencoded")) {
            try {
                String decodeBody = URLDecoder.decode(new String(body, CHARSET), CHARSET);
                body = decodeBody.getBytes(CHARSET);
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                throw new IllegalArgumentException("인코딩 에러가 발생했습니다.");
            }
        }

        return HttpRequest.builder()
                .method(method)
                .path(path)
                .version(httpVersion.getVersion())
                .headers(headerMap)
                .body(new ByteArrayInputStream(body))
                .build();
    }
}
