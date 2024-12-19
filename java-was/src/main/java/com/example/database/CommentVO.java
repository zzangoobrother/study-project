package com.example.database;

import java.time.LocalDateTime;

public record CommentVO(
        Long commentId,
        Long postId,
        Long userId,
        String content,
        LocalDateTime createdDate
) {
}
