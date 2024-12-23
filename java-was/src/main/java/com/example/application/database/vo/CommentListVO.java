package com.example.application.database.vo;

import java.time.LocalDateTime;

public record CommentListVO(
        Long commentId,
        Long postId,
        Long userId,
        String nickname,
        String content,
        LocalDateTime createdDate
) {
}
