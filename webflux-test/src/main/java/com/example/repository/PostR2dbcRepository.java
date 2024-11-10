package com.example.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PostR2dbcRepository extends ReactiveCrudRepository<Post, Long>, PostCustomR2dbcRepository {

    Flux<Post> findByUserId(Long userId);
}
