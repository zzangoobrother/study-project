package com.example.controller;

import com.example.dto.PostCreateRequest;
import com.example.dto.PostResponseV2;
import com.example.service.PostServiceV2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequestMapping("/v2/posts")
@RestController
public class PostControllerV2 {

    private final PostServiceV2 postServiceV2;

    public PostControllerV2(PostServiceV2 postServiceV2) {
        this.postServiceV2 = postServiceV2;
    }

    @PostMapping
    public Mono<PostResponseV2> create(@RequestBody PostCreateRequest request) {
        return postServiceV2.create(request.userId(), request.title(), request.content())
                .map(PostResponseV2::of);
    }

    @GetMapping
    public Flux<PostResponseV2> findAll() {
        return postServiceV2.findAll()
                .map(PostResponseV2::of);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<PostResponseV2>> findPost(@PathVariable("id") Long id) {
        return postServiceV2.findById(id)
                .map(p -> ResponseEntity.ok().body(PostResponseV2.of(p)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable("id") Long id) {
        return postServiceV2.deleteById(id).then(Mono.just(ResponseEntity.noContent().build()));
    }
}
