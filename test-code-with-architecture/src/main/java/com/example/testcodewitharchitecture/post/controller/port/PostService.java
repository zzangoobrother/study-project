package com.example.testcodewitharchitecture.post.controller.port;

import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;

public interface PostService {

    Post getById(long id);

    Post create(PostCreate postCreateDto);

    Post update(long id, PostUpdate postUpdateDto);
}
