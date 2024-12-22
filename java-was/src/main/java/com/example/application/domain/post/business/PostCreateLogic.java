package com.example.application.domain.post.business;

import com.example.application.domain.post.model.Post;
import com.example.application.domain.post.request.PostCreateRequest;
import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.application.database.dao.PostDao;
import com.example.webserver.http.Session;
import com.example.application.mapper.PostMapper;
import com.example.application.processor.Triggerable;
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
