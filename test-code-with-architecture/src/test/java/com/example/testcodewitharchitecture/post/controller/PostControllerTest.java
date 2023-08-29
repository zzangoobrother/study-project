package com.example.testcodewitharchitecture.post.controller;

import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestContainer;
import com.example.testcodewitharchitecture.mock.TestUuidHolder;
import com.example.testcodewitharchitecture.post.controller.response.PostResponse;
import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostControllerTest {

    @Test
    void 사용자는_게시물을_단건_조회_할_수_있다() throws Exception {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build();

        testContainer.userRepository.save(user);

        testContainer.postRepository.save(Post.builder()
                .id(1L)
                .content("helloworld")
                .writer(user)
                .createdAt(100L)
                .build());

        ResponseEntity<PostResponse> response = testContainer.postController.getById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEqualTo("helloworld");
        assertThat(response.getBody().getWriter().getId()).isEqualTo(1L);
        assertThat(response.getBody().getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getWriter().getNickname()).isEqualTo("abc");
    }

    @Test
    void 사용자는_게시물을_수정할_수_있다() throws Exception {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build();

        testContainer.userRepository.save(user);

        testContainer.postRepository.save(Post.builder()
                .id(1L)
                .content("helloworld")
                .writer(user)
                .createdAt(100L)
                .build());

        PostUpdate postUpdate = PostUpdate.builder().content("abcde").build();

        ResponseEntity<PostResponse> response = testContainer.postController.update(1L, postUpdate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEqualTo("abcde");
        assertThat(response.getBody().getWriter().getId()).isEqualTo(1L);
        assertThat(response.getBody().getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getWriter().getNickname()).isEqualTo("abc");
    }

    @Test
    void 사용자가_존재하지_않는_게시물을_조회할_경우_에러가_난다() throws Exception {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build();

        testContainer.userRepository.save(user);

        assertThatThrownBy(() -> testContainer.postController.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}