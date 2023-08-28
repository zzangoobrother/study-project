package com.example.testcodewitharchitecture.user.domain;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestUuidHolder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void User는_UserCreate_객체로_생성할_수_있다() {
        UserCreate userCreate = UserCreate.builder()
                .email("abcde@naver.com")
                .address("Gyeongi")
                .nickname("abcde")
                .build();

        User user = User.from(userCreate, new TestUuidHolder("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"));

        assertThat(user.getEmail()).isEqualTo(userCreate.getEmail());
        assertThat(user.getAddress()).isEqualTo(userCreate.getAddress());
        assertThat(user.getNickname()).isEqualTo(userCreate.getNickname());
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getCertificationCode()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa");
    }

    @Test
    void User는_UserUpdate_객체로_데이터를_업데이트_할_수_있다() {
        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .status(UserStatus.ACTIVE)
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaab")
                .lastLoginAt(100L)
                .build();

        UserUpdate userUpdate = UserUpdate.builder()
                .address("Namy")
                .nickname("abc")
                .build();

        user = user.update(userUpdate);

        assertThat(user.getAddress()).isEqualTo(userUpdate.getAddress());
        assertThat(user.getNickname()).isEqualTo(userUpdate.getNickname());
    }

    @Test
    void User는_로그인을_할_수_있고_로그인시_마지막_로그인_시간이_변경된다() {
        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .status(UserStatus.ACTIVE)
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaab")
                .lastLoginAt(100L)
                .build();

        user = user.login(new TestClockHolder(10000L));

        assertThat(user.getLastLoginAt()).isEqualTo(10000L);
    }

    @Test
    void User는_유효한_인증_코드로_계정을_활성화_할_수_있다() {
        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .status(UserStatus.PENDING)
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaab")
                .lastLoginAt(100L)
                .build();

        user = user.certificate("aaaaaaaa-aaaa-aaaa-aaaaaaaaab");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void User는_잘못된_인증_코드로_계정을_활성화_할_수_없다() {
        User user = User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .status(UserStatus.PENDING)
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaab")
                .lastLoginAt(100L)
                .build();

        assertThatThrownBy(() -> user.certificate("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"))
                .isInstanceOf(CertificationCodeNotMatchedException.class);
    }
}