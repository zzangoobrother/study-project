package com.example.helloboot;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@HellobootTest
class HelloRepositoryTest {

    @Autowired
    HelloRepository helloRepository;

    @Test
    void findHelloFailed() {
        Assertions.assertThat(helloRepository.findHello("seon")).isNull();
    }

    @Test
    void increaseCount() {
        Assertions.assertThat(helloRepository.countOf("seon")).isEqualTo(0);

        helloRepository.increaseCount("seon");
        Assertions.assertThat(helloRepository.countOf("seon")).isEqualTo(1);

        helloRepository.increaseCount("seon");
        Assertions.assertThat(helloRepository.countOf("seon")).isEqualTo(2);
    }
}
