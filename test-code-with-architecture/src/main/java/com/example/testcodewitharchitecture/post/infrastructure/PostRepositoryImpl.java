package com.example.testcodewitharchitecture.post.infrastructure;

import com.example.testcodewitharchitecture.post.domain.Post;
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
    public Optional<Post> findById(long id) {
        return jpaPostRepository.findById(id).map(PostEntity::toModel);
    }

    @Override
    public Post save(Post post) {
        return jpaPostRepository.save(PostEntity.fromModel(post)).toModel();
    }
}
