package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.header.HeaderConstants;
import com.example.web.user.response.UserInfoResponse;

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
