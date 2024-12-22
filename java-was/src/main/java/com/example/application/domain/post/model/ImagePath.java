package com.example.application.domain.post.model;

public class ImagePath {
    private final String value;

    public ImagePath(String path) {
        validatePath(path);
        this.value = path;
    }

    private void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("이미지 경로가 없습니다.");
        }
    }

    public String getValue() {
        return value;
    }
}
