package com.example.application.domain.comment.business;

import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.application.database.dao.CommentDao;
import com.example.application.domain.comment.model.Comment;
import com.example.application.domain.comment.request.CreateCommentRequest;
import com.example.webserver.http.Session;
import com.example.application.mapper.CommentMapper;
import com.example.application.processor.Triggerable;

public class CreateCommentLogic implements Triggerable<CreateCommentRequest, Void> {

    private final CommentDao commentDao;

    public CreateCommentLogic(CommentDao commentDao) {
        this.commentDao = commentDao;
    }

    @Override
    public Void run(CreateCommentRequest request) {
        createComment(request);
        return null;
    }

    private void createComment(CreateCommentRequest request) {
        Session session = AuthorizationContextHolder.getContextHolder().getSession();

        Long userId = session.getUserId();
        Comment comment = new Comment(request.postId(), userId, request.content(), null);

        commentDao.save(CommentMapper.toCommentVO(comment));
    }
}
