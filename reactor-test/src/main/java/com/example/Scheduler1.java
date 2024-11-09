package com.example;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class Scheduler1 {

    public Flux<Integer> fluxMapWithSubscribeOn() {
        return Flux.range(1, 10)
                .map(i -> i * 2)
                .subscribeOn(Schedulers.boundedElastic())
                .log();
    }

    public Flux<Integer> fluxMapWithSPublishOn() {
        return Flux.range(1, 10)
                .map(i -> i * 1)
                .log()
                .publishOn(Schedulers.boundedElastic())
                .map(i -> i * 2)
                .log();
    }
}
