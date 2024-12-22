package com.example.application.domain.comment.response;

import java.util.List;

public record CommentListResponse(
        Long postId,
        List<CommentResponse> commentList,
        Long commentCount
) {

    public static CommentListResponse of(Long postId, List<CommentResponse> commentList) {
        return new CommentListResponse(postId, commentList, (long) commentList.size());
    }
}
