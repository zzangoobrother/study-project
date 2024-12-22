package com.example.application.domain.post.handler;

import com.example.application.domain.post.request.PostCreateRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.application.processor.ArgumentResolver;

import java.io.IOException;

public class PostCreateRequestHandler extends ApiRequestHandler<PostCreateRequest, Void> {

    private final ArgumentResolver<PostCreateRequest> argumentResolver;

    public PostCreateRequestHandler(ArgumentResolver<PostCreateRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public PostCreateRequest resolveArgument(HttpRequest httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(PostCreateRequest request, Void response, HttpRequest httpRequest, HttpResponse httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) throws IOException {

    }
}
