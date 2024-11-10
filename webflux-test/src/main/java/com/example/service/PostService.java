package com.example.service;

import com.example.client.PostClient;
import com.example.dto.PostResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class PostService {

    private PostClient postClient;

    public PostService(PostClient postClient) {
        this.postClient = postClient;
    }

    public Mono<PostResponse> getPostContent(Long id) {
        return postClient.getPost(id)
                .onErrorResume(error -> Mono.just(new PostResponse(id.toString(), "Fallback Data %d".formatted(id))));
    }

    public Flux<PostResponse> getMultiplePostContent(List<Long> ids) {
        return Flux.fromIterable(ids)
                .flatMap(this::getPostContent);
    }

    public Flux<PostResponse> getParallelMultiplePostContent(List<Long> ids) {
        return Flux.fromIterable(ids)
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(this::getPostContent)
                .sequential();
    }
}
