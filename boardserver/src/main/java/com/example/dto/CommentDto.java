package com.example.dto;

public record CommentDto(
        int id,
        int postId,
        String contents,
        int subCommentId
) {
}
