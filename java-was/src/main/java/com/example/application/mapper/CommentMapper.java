package com.example.application.mapper;

import com.example.application.database.CommentVO;
import com.example.application.domain.comment.model.Comment;

public class CommentMapper {

    public static CommentVO toCommentVO(Comment comment) {
        return new CommentVO(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getUserId(),
                comment.getContent().getValue(),
                comment.getCreatedAt()
        );
    }

    public static Comment toEntity(CommentVO commentVO) {
        return new Comment(
                commentVO.postId(),
                commentVO.userId(),
                commentVO.content(),
                commentVO.createdDate()
        );
    }
}
