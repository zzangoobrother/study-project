package com.example.application.domain.comment.argumentresolver;

import com.example.api.Request;
import com.example.application.domain.comment.request.CreateCommentRequest;
import com.example.application.helper.JsonDeserializer;
import com.example.application.processor.ArgumentResolver;
import com.example.webserver.http.Path;

import java.util.Map;

public class CreateCommentArgumentResolver implements ArgumentResolver<CreateCommentRequest> {
    @Override
    public CreateCommentRequest resolve(Request request) {
        Path path = request.getPath();
        Long postId = Long.valueOf(path.getSegments().get(2));

        String body = new String(request.getBody().readAllBytes());

        Map<String, String> stringStringMap = JsonDeserializer.simpleConvertJsonToMap(body);

        String content = stringStringMap.get("content");
        if (postId == null || content == null) {
            throw new RuntimeException("필수 파라미터가 누락되었습니다.");
        }

        return new CreateCommentRequest(postId, content);
    }
}
