package com.example.application.domain.post.response;

import com.example.application.domain.comment.response.CommentListResponse;

public record PostResponse(
        Long postId,
        String nickname,
        String content,
        String imageName,
        CommentListResponse commentList
) {
}
