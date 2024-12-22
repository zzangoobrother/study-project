package com.example.application.domain.post.response;

import java.util.List;

public record PostListResponse(
        List<PostResponse> postResponses,
        int totalCount
) {

    public static PostListResponse of(List<PostResponse> postResponses) {
        return new PostListResponse(postResponses, postResponses.size());
    }
}
