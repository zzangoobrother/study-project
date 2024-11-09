package com.example;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class Operator3 {

    public Mono<Long> fluxCount() {
        return Flux.range(1, 10).count().log();
    }

    public Flux<String> fluxDistinct() {
        return Flux.fromIterable(List.of("a", "b", "a", "b", "c"))
                .distinct()
                .log();
    }

    public Mono<Integer> fluxReduce() {
        return Flux.range(1, 10)
                .reduce((i, j) -> i + j)
                .log();
    }

    public Flux<Integer> fluxGroupBy() {
        return Flux.range(1, 10)
                .groupBy(i -> (i % 2 == 0) ? "even" : "add")
                .flatMap(group -> group.reduce(Integer::sum))
                .log();
    }
}
