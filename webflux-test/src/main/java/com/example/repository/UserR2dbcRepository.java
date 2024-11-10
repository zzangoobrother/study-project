package com.example.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserR2dbcRepository extends ReactiveCrudRepository<User, Long> {

    Flux<User> findByName(String name);

    Flux<User> findByNameOrderByIdDesc(String name);

    @Modifying
    @Query("delete from users where name = :name")
    Mono<Void> deleteByName(String name);
}
