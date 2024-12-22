package com.example.application.domain.post.request;

public record PostCreateRequest(
        String content,
        String imageName,
        byte[] image
) {
}
