package com.example.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    Member member;
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        this.passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(String password) {
                return password.toUpperCase();
            }

            @Override
            public boolean matches(String password, String passwordHash) {
                return encode(password).equals(passwordHash);
            }
        };

        member = Member.register(new MemberCreateRequest("choisk@splearn.app", "choi", "secret"), passwordEncoder);
    }

    @Test
    void createMember() {
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
    }

    @DisplayName("회원 상태 activate 변경")
    @Test
    void activate() {
        // when
        member.activate();

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @DisplayName("회원 상태 activate 두번 변경 실패")
    @Test
    void activateFail() {
        // when
        member.activate();

        // then
        assertThatThrownBy(member::activate).isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("회원 상태 deactivated 변경은 activate 상태일 때만 변경 가능하다")
    @Test
    void deactivated() {
        // given
        member.activate();

        // when
        member.deactivated();

        // then
        assertThatThrownBy(member::deactivated).isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("회원 상태 deactivated 변경 중 실패")
    @Test
    void deactivatedFail() {
        assertThatThrownBy(member::deactivated).isInstanceOf(IllegalStateException.class);

        member.activate();
        member.deactivated();

        assertThatThrownBy(member::deactivated).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verifyPassword() {
        assertThat(member.verifyPassword("secret", passwordEncoder)).isTrue();
        assertThat(member.verifyPassword("hello", passwordEncoder)).isFalse();
    }

    @Test
    void changeNickname() {
        assertThat(member.getNickname()).isEqualTo("choi");

        member.changeNickname("sk");

        assertThat(member.getNickname()).isEqualTo("sk");
    }

    @Test
    void changePassword() {
        member.changePassword("verysecret", passwordEncoder);

        assertThat(member.verifyPassword("verysecret", passwordEncoder)).isTrue();
    }

    @Test
    void isActive() {
        assertThat(member.isActive()).isFalse();

        member.activate();

        assertThat(member.isActive()).isTrue();

        member.deactivated();

        assertThat(member.isActive()).isFalse();
    }

    @Test
    void invalidEmail() {
        assertThatThrownBy(() -> Member.register(new MemberCreateRequest("invalid email", "choi", "secret"), passwordEncoder))
                .isInstanceOf(IllegalArgumentException.class);

        Member.register(new MemberCreateRequest("choi@gmail.com", "choi", "secret"), passwordEncoder);
    }
}
