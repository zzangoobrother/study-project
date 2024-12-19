package com.example.database;

public record PostVO(
        Long postId,
        Long userId,
        String content,
        String imagePath
) {
}
