package com.example.domain.post.business;

import com.example.database.CommentVO;
import com.example.database.dao.CommentDao;
import com.example.database.dao.PostDao;
import com.example.domain.comment.response.CommentListResponse;
import com.example.domain.comment.response.CommentResponse;
import com.example.domain.post.response.PostListResponse;
import com.example.domain.post.response.PostResponse;
import com.example.processor.Triggerable;

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
                    List<CommentVO> commentsOfPost = commentDao.findByPostId(it.postId());

                    List<CommentResponse> commentResponses = commentsOfPost.stream()
                            .map(comment -> new CommentResponse(comment.commentId(), "commentNickname", comment.content()))
                            .toList();

                    return new PostResponse(it.postId(), it.nickname(), it.content(), it.imagePath(), CommentListResponse.of(it.postId(), commentResponses));
                })
                .toList();

        Collections.reverse(postResponses);
        return PostListResponse.of(postResponses);
    }
}
