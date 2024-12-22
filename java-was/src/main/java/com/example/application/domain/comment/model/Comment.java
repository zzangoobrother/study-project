package com.example.application.domain.comment.model;

import java.time.LocalDateTime;

public class Comment {
    private Long commentId;
    private Long postId;
    private Long userId;
    private CommentContent content;
    private LocalDateTime createdAt;

    public Comment(Long postId, Long userId, String content, LocalDateTime createdAt) {
        this.postId = postId;
        this.userId = userId;
        this.content = new CommentContent(content);
        this.createdAt = createdAt;
    }

    public void initCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public CommentContent getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
