package com.example.hellospring.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientApiExecutor implements ApiExecutor {
    @Override
    public String execute(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        try {
            return HttpClient
                    .newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
