package com.example.application.domain.user.handler;

import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.header.HeaderConstants;
import com.example.application.domain.user.response.UserInfoResponse;

public class GetUserInfoRequestHandler extends ApiRequestHandler<Void, UserInfoResponse> {

    @Override
    public String serializeResponse(UserInfoResponse response) {
        return """
                {
                    "name" : "%s"
                }
                """.formatted(response.getName());
    }

    @Override
    public Void resolveArgument(HttpRequest httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, UserInfoResponse response, HttpRequest httpRequest, HttpResponse httpResponse) {
        httpResponse.getHttpHeaders().addHeader(HeaderConstants.CONTENT_TYPE, "application/json");
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {

    }
}
