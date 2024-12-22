package com.example.application.database;

public record PostListVO(
        Long postId,
        Long userId,
        String nickname,
        String content,
        String imagePath
) {
}
