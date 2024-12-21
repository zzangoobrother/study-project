package com.example.domain.post.argumentresolver;

import com.example.domain.post.request.PostCreateRequest;
import com.example.http.HttpRequest;
import com.example.http.header.HttpHeaders;
import com.example.processor.resolver.ArgumentResolver;
import com.example.webserver.helper.MultiPartParseHelper;

import java.io.IOException;
import java.util.Map;

public class PostCreateArgumentResolver implements ArgumentResolver<PostCreateRequest> {
    @Override
    public PostCreateRequest resolve(HttpRequest httpRequest) {
        HttpHeaders headers = httpRequest.getHeaders();
        String boundary = headers.getMultipartBoundary()
                .orElseThrow(() -> new RuntimeException("Content-Type이 multipart/form-data가 아닙니다."));

        try {
            Map<String, MultiPartParseHelper.MultiPart> parse = MultiPartParseHelper.parse(httpRequest.getBody(), boundary);

            String content = parse.get("content").getTextContent();

            String imageName = parse.get("image").getFilename();
            byte[] image = parse.get("image").getContent();
            return new PostCreateRequest(content, imageName, image);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
