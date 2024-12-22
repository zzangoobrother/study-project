package com.example.application.domain.user.handler;

import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpStatus;
import com.example.application.domain.user.response.UserListResponse;

import java.util.stream.Collectors;

public class GetUserListRequestHandler extends ApiRequestHandler<Void, UserListResponse> {

    @Override
    public String serializeResponse(UserListResponse response) {
        String userListJson = response.getUserList().stream()
                .map(it -> """
                        {
                            "name" : "%s",
                            "email" : "%s"
                        }
                        """.formatted(it.getName(), it.getEmail()))
                .collect(Collectors.joining(", ", "[", "]"));

        return """
                {
                    "count" : %d,
                    "userList" : %s
                }
                """.formatted(response.getCount(), userListJson);
    }

    @Override
    public Void resolveArgument(HttpRequest httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, UserListResponse response, HttpRequest httpRequest, HttpResponse httpResponse) {
        httpResponse.getHttpHeaders().addHeader("Content-Type", "application/json");
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {

    }
}
