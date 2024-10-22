package com.example.helloboot;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
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
