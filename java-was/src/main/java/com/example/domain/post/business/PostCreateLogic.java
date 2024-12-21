package com.example.domain.post.business;

import com.example.authorization.AuthorizationContext;
import com.example.authorization.AuthorizationContextHolder;
import com.example.database.dao.PostDao;
import com.example.domain.post.model.Post;
import com.example.domain.post.request.PostCreateRequest;
import com.example.http.Session;
import com.example.mapper.PostMapper;
import com.example.processor.Triggerable;
import com.example.webserver.helper.FileSaveHelper;

public class PostCreateLogic implements Triggerable<PostCreateRequest, Void> {

    private final PostDao postDao;

    public PostCreateLogic(PostDao postDao) {
        this.postDao = postDao;
    }

    @Override
    public Void run(PostCreateRequest request) {
        createPost(request);
        return null;
    }

    private void createPost(PostCreateRequest request) {
        Session session = AuthorizationContextHolder.getContextHolder().getSession();
        if (session == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        Long userId = session.getUserId();

        try {
            String filename = FileSaveHelper.saveFile(request.image(), request.imageName());

            Post post = new Post(userId, request.content(), filename);

            postDao.save(PostMapper.toPostVO(post));
        } catch (Exception e) {
            throw new RuntimeException("파일 저장에 실패했습니다." + e.getMessage());
        }
    }
}
