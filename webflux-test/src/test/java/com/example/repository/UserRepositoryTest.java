package com.example.repository;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRepositoryTest {

    private final UserRepository userRepository = new UserRepositoryImpl();

    @Test
    void save() {
        User user = User.builder().name("홍길동").email("abcd@gmail.com").build();
        StepVerifier.create(userRepository.save(user))
                .assertNext(u -> {
                    assertEquals(1L, u.getId());
                    assertEquals("홍길동", u.getName());
                })
                .verifyComplete();
    }

    @Test
    void findAll() {
        userRepository.save(User.builder().name("홍길동1").email("abcd@gmail.com").build());
        userRepository.save(User.builder().name("홍길동2").email("abcd@gmail.com").build());
        userRepository.save(User.builder().name("홍길동3").email("abcd@gmail.com").build());

        StepVerifier.create(userRepository.findAll())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void findById() {
        userRepository.save(User.builder().name("홍길동").email("abcd@gmail.com").build());
        userRepository.save(User.builder().name("홍길동2").email("abcd@gmail.com").build());

        StepVerifier.create(userRepository.findById(1L))
                .assertNext(u -> {
                    assertEquals(1L, u.getId());
                    assertEquals("홍길동", u.getName());
                })
                .verifyComplete();
    }

    @Test
    void deleteById() {
        userRepository.save(User.builder().name("홍길동").email("abcd@gmail.com").build());
        userRepository.save(User.builder().name("홍길동2").email("abcd@gmail.com").build());

        StepVerifier.create(userRepository.deleteById(1L))
                .expectNext(1)
                .verifyComplete();
    }
}
