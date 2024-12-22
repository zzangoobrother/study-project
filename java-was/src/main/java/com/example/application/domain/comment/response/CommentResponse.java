package com.example.application.domain.comment.response;

public record CommentResponse(
        Long commentId,
        String nickname,
        String content
) {
}
