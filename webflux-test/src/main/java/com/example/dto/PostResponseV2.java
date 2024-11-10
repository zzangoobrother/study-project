package com.example.dto;

import com.example.repository.Post;
import lombok.Builder;

import java.time.LocalDateTime;

public record PostResponseV2(
        Long id,
        Long userId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    @Builder
    public PostResponseV2 {}

    public static PostResponseV2 of(Post post) {
        return PostResponseV2.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCratedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
