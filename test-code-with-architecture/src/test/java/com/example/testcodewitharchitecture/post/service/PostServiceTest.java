package com.example.testcodewitharchitecture.post.service;

import com.example.testcodewitharchitecture.mock.FakePostRepository;
import com.example.testcodewitharchitecture.mock.FakeUserRepository;
import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostServiceTest {

    private PostService postService;

    @BeforeEach
    void init() {
        FakeUserRepository repository = new FakeUserRepository();

        User user1 = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build();

        User user2 = User.builder()
                .id(2L)
                .email("abcd@naver.com")
                .nickname("abcd")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaab")
                .status(UserStatus.PENDING)
                .lastLoginAt(0L)
                .build();

        repository.save(user1);
        repository.save(user2);

        FakePostRepository postRepository = new FakePostRepository();
        postService = new PostService(postRepository, repository, new TestClockHolder(10000L));

        Post post = Post.builder()
                .id(1L)
                .content("helloword")
                .createdAt(1678530673958L)
                .modifiedAt(1678530673958L)
                .writer(user1)
                .build();

        postRepository.save(post);
    }

    @Test
    void getById는_존재하는_게시물을_내려준다() {
        // given
        // when
        Post result = postService.getById(1);

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
        Post result = postService.create(postCreateDto);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getContent()).isEqualTo("foobar");
        assertThat(result.getCreatedAt()).isEqualTo(10000L);
    }

    @Test
    void postUpdateDto_를_이용하여_게시물을_수정할_수_있다() {
        // given
        PostUpdate postUpdateDto = PostUpdate.builder()
                .content("hello world : ")
                .build();

        // when
        postService.update(1, postUpdateDto);

        // then
        Post result = postService.getById(1);
        assertThat(result.getContent()).isEqualTo("hello world : ");
        assertThat(result.getModifiedAt()).isEqualTo(10000L);
    }
}