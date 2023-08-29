package com.example.testcodewitharchitecture.post.controller;

import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestContainer;
import com.example.testcodewitharchitecture.mock.TestUuidHolder;
import com.example.testcodewitharchitecture.post.controller.response.PostResponse;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PostCreateControllerTest {

    @Test
    void 사용자는_게시물을_작성할_수_있다() throws Exception {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        testContainer.userRepository.save(User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build());

        PostCreate postCreateDto = PostCreate.builder()
                .writerId(1)
                .content("helloworld")
                .build();

        ResponseEntity<PostResponse> response = testContainer.postCreateController.create(postCreateDto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEqualTo("helloworld");
        assertThat(response.getBody().getWriter().getId()).isEqualTo(1L);
        assertThat(response.getBody().getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getWriter().getNickname()).isEqualTo("abc");
    }
}