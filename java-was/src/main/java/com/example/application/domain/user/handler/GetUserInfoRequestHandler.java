package com.example.application.domain.user.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.user.response.UserInfoResponse;
import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.header.HeaderConstants;

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
    public Void resolveArgument(Request httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, UserInfoResponse response, Request httpRequest, Response httpResponse) {
        httpResponse.getHttpHeaders().addHeader(HeaderConstants.CONTENT_TYPE, "application/json");
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) {

    }
}
