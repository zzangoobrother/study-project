package com.example.testcodewitharchitecture.post.controller.response;

import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.user.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostResponseTest {

    @Test
    void Post로_응답을_생성할_수_있다() {
        Post post = Post.builder()
                .content("helloworld")
                .writer(User.builder()
                        .email("abc@naver.com")
                        .nickname("abc")
                        .build()
                )
                .build();

        PostResponse postResponse = PostResponse.from(post);

        assertThat(postResponse.getContent()).isEqualTo("helloworld");
        assertThat(postResponse.getWriter().getEmail()).isEqualTo("abc@naver.com");
        assertThat(postResponse.getWriter().getNickname()).isEqualTo("abc");
    }
}