package com.example;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class Operator3Test {

    private Operator3 operator3 = new Operator3();

    @Test
    void monoZip() {
        StepVerifier.create(operator3.fluxCount())
                .expectNext(10L)
                .verifyComplete();
    }

    @Test
    void fluxDistinct() {
        StepVerifier.create(operator3.fluxDistinct())
                .expectNext("a", "b", "c")
                .verifyComplete();
    }

    @Test
    void fluxReduce() {
        StepVerifier.create(operator3.fluxReduce())
                .expectNext(55)
                .verifyComplete();
    }

    @Test
    void fluxGroupBy() {
        StepVerifier.create(operator3.fluxGroupBy())
                .expectNext(25)
                .expectNext(30)
                .verifyComplete();
    }
}
