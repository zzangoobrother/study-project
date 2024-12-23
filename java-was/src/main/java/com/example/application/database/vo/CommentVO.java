package com.example.application.database.vo;

import java.time.LocalDateTime;

public record CommentVO(
        Long commentId,
        Long postId,
        Long userId,
        String content,
        LocalDateTime createdDate
) {
}
