package com.example;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class Scheduler1Test {

    private Scheduler1 scheduler1 = new Scheduler1();

    @Test
    void fluxMapWithSubscribeOn() {
        StepVerifier.create(scheduler1.fluxMapWithSubscribeOn())
                .expectNextCount(10)
                .verifyComplete();
    }

    @Test
    void fluxMapWithSPublishOn() {
        StepVerifier.create(scheduler1.fluxMapWithSPublishOn())
                .expectNextCount(10)
                .verifyComplete();
    }
}
