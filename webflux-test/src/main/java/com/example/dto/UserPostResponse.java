package com.example.dto;

import com.example.repository.Post;
import lombok.Builder;

import java.time.LocalDateTime;

public record UserPostResponse(
        Long id,
        String userName,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    @Builder
    public UserPostResponse {}

    public static UserPostResponse of(Post post) {
        return UserPostResponse.builder()
                .id(post.getId())
                .userName(post.getUser().getName())
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCratedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
