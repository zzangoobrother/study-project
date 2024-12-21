package com.example.domain.post.handler;

import com.example.domain.post.request.PostCreateRequest;
import com.example.handler.ApiRequestHandler;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.processor.resolver.ArgumentResolver;

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
