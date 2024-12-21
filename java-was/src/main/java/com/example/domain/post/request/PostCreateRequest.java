package com.example.domain.post.request;

public record PostCreateRequest(
        String content,
        String imageName,
        byte[] image
) {
}
