package com.example.testcodewitharchitecture.post.infrastructure;

import com.example.testcodewitharchitecture.post.service.port.PostRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostRepositoryImpl implements PostRepository {

    private final JpaPostRepository jpaPostRepository;

    public PostRepositoryImpl(JpaPostRepository jpaPostRepository) {
        this.jpaPostRepository = jpaPostRepository;
    }

    @Override
    public Optional<PostEntity> findById(long id) {
        return jpaPostRepository.findById(id);
    }

    @Override
    public PostEntity save(PostEntity postEntity) {
        return jpaPostRepository.save(postEntity);
    }
}
