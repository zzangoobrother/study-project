package com.example.application.domain.comment.request;

public record CreateCommentRequest(
        Long postId,
        String content
) {
}
