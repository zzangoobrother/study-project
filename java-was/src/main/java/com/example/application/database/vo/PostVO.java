package com.example.application.database.vo;

public record PostVO(
        Long postId,
        Long userId,
        String content,
        String imagePath
) {
}
