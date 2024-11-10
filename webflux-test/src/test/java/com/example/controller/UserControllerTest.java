package com.example.controller;

import com.example.dto.UserCreateRequest;
import com.example.dto.UserResponse;
import com.example.dto.UserUpdateRequest;
import com.example.repository.User;
import com.example.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@AutoConfigureWebTestClient
@WebFluxTest(UserController.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @Test
    void createUser() {
        when(userService.create("홍길동", "abcd@gmail.com")).thenReturn(Mono.just(new User(1L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now())));

        webTestClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserCreateRequest("홍길동", "abcd@gmail.com"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(UserResponse.class)
                .value(res -> {
                    assertEquals("홍길동", res.name());
                    assertEquals("abcd@gmail.com", res.email());
                });
    }

    @Test
    void findAllUsers() {
        when(userService.findAll()).thenReturn(
                Flux.just(
                        new User(1L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now()),
                        new User(2L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now()),
                        new User(3L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now())
                )
        );

        webTestClient.get().uri("/users")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(UserResponse.class)
                .hasSize(3);
    }

    @Test
    void findUsers() {
        when(userService.findById(1L)).thenReturn(
                Mono.just(new User(1L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now()))
        );

        webTestClient.get().uri("/users/1")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(UserResponse.class)
                .value(res -> {
                    assertEquals("홍길동", res.name());
                    assertEquals("abcd@gmail.com", res.email());
                });
    }

    @Test
    void notFouondUsers() {
        when(userService.findById(1L)).thenReturn(
                Mono.empty()
        );

        webTestClient.get().uri("/users/1")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void deletedUsers() {
        when(userService.deleteById(1L)).thenReturn(
                Mono.empty()
        );

        webTestClient.delete().uri("/users/1")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Test
    void updateUser() {
        when(userService.update(1L, "홍길동", "abcd@gmail.com")).thenReturn(Mono.just(new User(1L, "홍길동", "abcd@gmail.com", LocalDateTime.now(), LocalDateTime.now())));

        webTestClient.put().uri("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserUpdateRequest("홍길동", "abcd@gmail.com"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody(UserResponse.class)
                .value(res -> {
                    assertEquals("홍길동", res.name());
                    assertEquals("abcd@gmail.com", res.email());
                });
    }
}
