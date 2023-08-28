package com.example.testcodewitharchitecture.post.service.port;

import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.infrastructure.PostEntity;

import java.util.Optional;

public interface PostRepository {
    Optional<Post> findById(long id);

    Post save(Post post);
}
