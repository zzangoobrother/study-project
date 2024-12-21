package com.example.domain.comment.response;

public record CommentResponse(
        Long commentId,
        String nickname,
        String content
) {
}
