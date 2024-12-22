package com.example.application.domain.post.handler;

import com.example.application.domain.post.response.PostListResponse;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.helper.JsonSerializer;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;

import java.io.IOException;

public class GetPostListRequestHandler extends ApiRequestHandler<Void, PostListResponse> {

    @Override
    public String serializeResponse(PostListResponse response) {
        return JsonSerializer.toJson(response)
    }

    @Override
    public Void resolveArgument(HttpRequest httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, PostListResponse response, HttpRequest httpRequest, HttpResponse httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) throws IOException {

    }
}
