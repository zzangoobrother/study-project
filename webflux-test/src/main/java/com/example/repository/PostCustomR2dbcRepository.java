package com.example.repository;

import reactor.core.publisher.Flux;

public interface PostCustomR2dbcRepository {

    Flux<Post> findAllByUserId(Long userId);
}
