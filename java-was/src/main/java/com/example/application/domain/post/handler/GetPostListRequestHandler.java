package com.example.application.domain.post.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.post.response.PostListResponse;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.helper.JsonSerializer;

import java.io.IOException;

public class GetPostListRequestHandler extends ApiRequestHandler<Void, PostListResponse> {

    @Override
    public String serializeResponse(PostListResponse response) {
        return JsonSerializer.toJson(response);
    }

    @Override
    public Void resolveArgument(Request httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, PostListResponse response, Request httpRequest, Response httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) throws IOException {

    }
}
