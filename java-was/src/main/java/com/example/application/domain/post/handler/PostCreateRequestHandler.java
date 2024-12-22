package com.example.application.domain.post.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.post.request.PostCreateRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.processor.ArgumentResolver;

import java.io.IOException;

public class PostCreateRequestHandler extends ApiRequestHandler<PostCreateRequest, Void> {

    private final ArgumentResolver<PostCreateRequest> argumentResolver;

    public PostCreateRequestHandler(ArgumentResolver<PostCreateRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public PostCreateRequest resolveArgument(Request httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(PostCreateRequest request, Void response, Request httpRequest, Response httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) throws IOException {

    }
}
