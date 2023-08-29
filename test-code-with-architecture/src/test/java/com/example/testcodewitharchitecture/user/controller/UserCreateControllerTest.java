package com.example.testcodewitharchitecture.user.controller;

import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestContainer;
import com.example.testcodewitharchitecture.mock.TestUuidHolder;
import com.example.testcodewitharchitecture.user.controller.response.UserResponse;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class UserCreateControllerTest {

    @Test
    void 사용자는_회원_가입을_할_수_있고_회원가입된_사용자는_PENDING_상태이다() {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        UserCreate userCreate = UserCreate.builder()
                .email("aaa@naver.com")
                .nickname("aaa")
                .address("Pang")
                .build();

        ResponseEntity<UserResponse> response = testContainer.userCreateController.create(userCreate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("aaa@naver.com");
        assertThat(response.getBody().getNickname()).isEqualTo("aaa");
        assertThat(response.getBody().getLastLoginAt()).isNull();
        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.PENDING);
    }
}