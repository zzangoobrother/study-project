package com.example;

import reactor.core.publisher.Flux;

import java.time.Duration;

public class Operator4 {

    public Flux<Integer> fluxDelayAndLimit() {
        return Flux.range(1, 10)
                .delaySequence(Duration.ofSeconds(1))
                .log()
                .limitRate(2);
    }

    public Flux<Integer> fluxSample() {
        return Flux.range(1, 100)
                .delayElements(Duration.ofMillis(100))
                .sample(Duration.ofMillis(300))
                .log();
    }
}
