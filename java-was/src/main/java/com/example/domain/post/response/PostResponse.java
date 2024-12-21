package com.example.domain.post.response;

import com.example.domain.comment.response.CommentListResponse;

public record PostResponse(
        Long postId,
        String nickname,
        String content,
        String imageName,
        CommentListResponse commentList
) {
}
