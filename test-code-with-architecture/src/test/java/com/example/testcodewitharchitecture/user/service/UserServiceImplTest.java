package com.example.testcodewitharchitecture.user.service;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.mock.FakeMailSender;
import com.example.testcodewitharchitecture.mock.FakeUserRepository;
import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestUuidHolder;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceImplTest {

    private UserServiceImpl userServiceImpl;

    @BeforeEach
    void init() {
        FakeMailSender mailSender = new FakeMailSender();
        FakeUserRepository repository = new FakeUserRepository();
        userServiceImpl = new UserServiceImpl(repository, new CertificationService(mailSender), new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaaa"), new TestClockHolder(10000L));

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
    }

    @Test
    void getByEmail은_ACTIVE_상태인_유저를_찾아올_수_있다() {
        // given
        String email = "abc@naver.com";

        // when
        User result = userServiceImpl.getByEmail(email);

        // then
        assertThat(result.getNickname()).isEqualTo("abc");
    }

    @Test
    void getByEmail은_PENDING_상태인_유저를_찾아올_수_없() {
        // given
        String email = "abcd@naver.com";

        // when
        // then
        assertThatThrownBy(() -> userServiceImpl.getByEmail(email)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById은_ACTIVE_상태인_유저를_찾아올_수_있다() {
        // given
        // when
        User result = userServiceImpl.getById(1L);

        // then
        assertThat(result.getNickname()).isEqualTo("abc");
    }

    @Test
    void getById은_PENDING_상태인_유저를_찾아올_수_없() {
        // given
        // when
        // then
        assertThatThrownBy(() -> userServiceImpl.getById(2)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userCreate_를_이용하여_유저를_생성할_수_있다() {
        // given
        UserCreate userCreate = UserCreate.builder()
                .email("abcde@naver.com")
                .address("Gyeongi")
                .nickname("abcde")
                .build();

        // when
        User result = userServiceImpl.create(userCreate);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.getCertificationCode()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaaaaaaaaa");
    }

    @Test
    void userUpdate_를_이용하여_유저를_수정할_수_있다() {
        // given
        UserUpdate userUpdate = UserUpdate.builder()
                .address("Inchenon")
                .nickname("abcde-10")
                .build();

        // when
        userServiceImpl.update(1, userUpdate);

        // then
        User result = userServiceImpl.getById(1);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getAddress()).isEqualTo("Inchenon");
        assertThat(result.getNickname()).isEqualTo("abcde-10");
    }

    @Test
    void user를_로그인_시키면_마지막_로그인_시간이_변경된다() {
        // given
        // when
        userServiceImpl.login(1);

        // then
        User result = userServiceImpl.getById(1);
        assertThat(result.getLastLoginAt()).isEqualTo(10000L);
    }

    @Test
    void PENDING_상태의_사용자는_인증_코드로_ACTIVE_시킬_수_있다() {
        // given
        // when
        userServiceImpl.verifyEmail(2, "aaaaaaaa-aaaa-aaaa-aaaaaaaaaab");

        // then
        User result = userServiceImpl.getById(2);
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void PENDING_상태의_사용자는_잘못된_인증_코드를_받으면_에러를_던진다() {
        // given
        // when
        // then
        assertThatThrownBy(() -> userServiceImpl.verifyEmail(2, "aaaaaaaa-aaaa-aaaa-aaaaaaaaaaa")).isInstanceOf(CertificationCodeNotMatchedException.class);
    }
}