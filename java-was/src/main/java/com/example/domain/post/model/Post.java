package com.example.domain.post.model;

public class Post {
    private Long postId;
    private Long userId;
    private Content content;
    private ImagePath imagePath;

    public Post(Long userId, String content, String imagePath) {
        validateUserId(userId);
        this.userId = userId;
        this.content = new Content(content);
        this.imagePath = new ImagePath(imagePath);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 아이디가 없습니다.");
        }
    }

    public void initPostId(Long postId) {
        this.postId = postId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public Content getContent() {
        return content;
    }

    public ImagePath getImagePath() {
        return imagePath;
    }
}
