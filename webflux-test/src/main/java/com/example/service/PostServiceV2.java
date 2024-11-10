package com.example.service;

import com.example.repository.Post;
import com.example.repository.PostR2dbcRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PostServiceV2 {

    private final PostR2dbcRepository repository;

    public PostServiceV2(PostR2dbcRepository repository) {
        this.repository = repository;
    }

    public Mono<Post> create(Long userId, String title, String content) {
        return repository.save(Post.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .build()
        );
    }

    public Flux<Post> findAll() {
        return repository.findAll();
    }

    public Mono<Post> findById(Long id) {
        return repository.findById(id);
    }

    public Flux<Post> findAllByUserId(Long userId) {
        return repository.findAllByUserId(userId);
    }

    public Mono<Void> deleteById(Long id) {
        return repository.deleteById(id);
    }
}
