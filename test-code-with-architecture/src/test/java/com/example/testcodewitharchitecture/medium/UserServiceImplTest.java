package com.example.testcodewitharchitecture.medium;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import com.example.testcodewitharchitecture.user.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@SqlGroup({
        @Sql(value = "/sql/user-service-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(value = "/sql/delete-all--data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
class UserServiceImplTest {

    @Autowired
    private UserServiceImpl userServiceImpl;

    @MockBean
    private JavaMailSender mailSender;

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

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // when
        User result = userServiceImpl.create(userCreate);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
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
        assertThat(result.getLastLoginAt()).isGreaterThan(0L);
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