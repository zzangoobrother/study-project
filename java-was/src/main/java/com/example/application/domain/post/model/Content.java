package com.example.application.domain.post.model;

public class Content {
    private final String value;

    public Content(String content) {
        validateContent(content);
        validateLength(content);
        this.value = content;
    }

    private void validateContent(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("내용이 없습니다.");
        }
    }

    private void validateLength(String content) {
        if (content.length() > 500) {
            throw new IllegalArgumentException("내용은 500자 이하여야 합니다.");
        }
    }

    public String getValue() {
        return value;
    }
}
