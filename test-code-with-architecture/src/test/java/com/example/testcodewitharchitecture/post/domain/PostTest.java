package com.example.testcodewitharchitecture.post.domain;

import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.user.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    void PostCreate으로_게시물을_만들_수_있다() {
        PostCreate postCreate = PostCreate.builder()
                .writerId(1)
                .content("helloworld")
                .build();

        User writer = User.builder()
                .email("abc@naver.com")
                .nickname("abc")
                .build();

        Post post = Post.from(postCreate, writer, new TestClockHolder(10000L));

        assertThat(post.getContent()).isEqualTo("helloworld");
        assertThat(post.getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(post.getWriter().getNickname()).isEqualTo("abc");
    }

    @Test
    void PostUpdate로_게시물을_수정할_수_있다() {
        PostUpdate postUpdate = PostUpdate.builder()
                .content("modify")
                .build();

        User writer = User.builder()
                .email("abc@naver.com")
                .nickname("abc")
                .build();

        Post post = Post.builder()
                .id(1L)
                .content("helloword")
                .createdAt(1678530673958L)
                .modifiedAt(1678530673958L)
                .writer(writer)
                .build();

        post = post.update(postUpdate, new TestClockHolder(10000L));

        assertThat(post.getContent()).isEqualTo("modify");
        assertThat(post.getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(post.getWriter().getNickname()).isEqualTo("abc");
    }
}