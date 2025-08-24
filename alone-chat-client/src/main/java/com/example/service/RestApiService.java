package com.example.service;

import com.example.dto.LoginRequest;
import com.example.dto.UserRegisterRequest;
import com.example.util.JsonUtil;
import lombok.Getter;
import org.glassfish.grizzly.http.util.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Getter
public class RestApiService {
    private final TerminalService terminalService;
    private final String url;
    private String sessionId;

    public RestApiService(TerminalService terminalService, String url) {
        this.terminalService = terminalService;
        this.url = "http://" + url;
    }

    public boolean register(String username, String password) {
        return request("/api/v1/auth/register", "", new UserRegisterRequest(username, password))
                .filter(response -> response.statusCode() == HttpStatus.OK_200.getStatusCode())
                .isPresent();
    }

    public boolean unregister() {
        if (sessionId.isEmpty()) {
            return false;
        }

        return request("/api/v1/auth/unregister", sessionId, null)
                .filter(response -> response.statusCode() == HttpStatus.OK_200.getStatusCode())
                .isPresent();
    }

    public boolean login(String username, String password) {
        return request("/api/v1/auth/login", "", new LoginRequest(username, password))
                .map(response -> {
                    if (response.statusCode() == HttpStatus.OK_200.getStatusCode()) {
                        sessionId = response.body();
                        return true;
                    }

                    return false;
                })
                .orElse(false);
    }

    public boolean logout() {
        if (sessionId.isEmpty()) {
            return false;
        }

        return request("/api/v1/auth/logout", sessionId, null)
                .filter(response -> response.statusCode() == HttpStatus.OK_200.getStatusCode())
                .isPresent();
    }

    private Optional<HttpResponse<String>> request(String path, String sessionId, Object request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(url + path))
                    .header("Content-Type", "application/json");

            if (!sessionId.isEmpty()) {
                builder.header("Cookie", "SESSION=" + sessionId);
            }

            if (request != null) {
                JsonUtil.toJson(request)
                        .ifPresent(body -> builder
                                .POST(HttpRequest.BodyPublishers.ofString(body)));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString());

            terminalService.printSystemMessage("Response status : %d, Body : %s".formatted(response.statusCode(), response.body()));

            return Optional.of(response);
        } catch (Exception ex) {
            terminalService.printSystemMessage("API call fail. cause : %s".formatted(ex.getMessage()));
            return Optional.empty();
        }
    }
}
