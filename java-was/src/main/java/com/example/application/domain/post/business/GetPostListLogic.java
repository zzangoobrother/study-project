package com.example.application.domain.post.business;

import com.example.application.database.dao.CommentDao;
import com.example.application.database.dao.PostDao;
import com.example.application.database.vo.CommentListVO;
import com.example.application.domain.comment.response.CommentListResponse;
import com.example.application.domain.comment.response.CommentResponse;
import com.example.application.domain.post.response.PostListResponse;
import com.example.application.domain.post.response.PostResponse;
import com.example.application.processor.Triggerable;

import java.util.Collections;
import java.util.List;

public class GetPostListLogic implements Triggerable<Void, PostListResponse> {

    private final PostDao postDao;
    private final CommentDao commentDao;

    public GetPostListLogic(PostDao postDao, CommentDao commentDao) {
        this.postDao = postDao;
        this.commentDao = commentDao;
    }

    @Override
    public PostListResponse run(Void unused) {
        List<PostResponse> postResponses = postDao.findAllJoinFetch().stream()
                .map(it -> {
                    List<CommentListVO> commentsOfPost = commentDao.findCommentsJoinFetch(it.postId());

                    List<CommentResponse> commentResponses = commentsOfPost.stream()
                            .map(comment -> new CommentResponse(comment.commentId(), comment.nickname(), comment.content()))
                            .toList();

                    return new PostResponse(it.postId(), it.nickname(), it.content(), it.imagePath(), CommentListResponse.of(it.postId(), commentResponses));
                })
                .toList();

        Collections.reverse(postResponses);
        return PostListResponse.of(postResponses);
    }
}
