package com.example.testcodewitharchitecture.medium;

import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.post.service.PostServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@SqlGroup({
        @Sql(value = "/sql/post-service-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(value = "/sql/delete-all--data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
class PostServiceImplTest {

    @Autowired
    private PostServiceImpl postServiceImpl;

    @Test
    void getById는_존재하는_게시물을_내려준다() {
        // given
        // when
        Post result = postServiceImpl.getById(1);

        // then
        assertThat(result.getContent()).isEqualTo("helloword");
        assertThat(result.getWriter().getEmail()).isEqualTo("abc@naver.com");
    }

    @Test
    void postCreateDto_를_이용하여_게시물을_생성할_수_있다() {
        // given
        PostCreate postCreateDto = PostCreate.builder()
                .writerId(1)
                .content("foobar")
                .build();

        // when
        Post result = postServiceImpl.create(postCreateDto);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getContent()).isEqualTo("foobar");
        assertThat(result.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    void postUpdateDto_를_이용하여_게시물을_수정할_수_있다() {
        // given
        PostUpdate postUpdateDto = PostUpdate.builder()
                .content("hello world : ")
                .build();

        // when
        postServiceImpl.update(1, postUpdateDto);

        // then
        Post result = postServiceImpl.getById(1);
        assertThat(result.getContent()).isEqualTo("hello world : ");
        assertThat(result.getModifiedAt()).isGreaterThan(0);
    }
}