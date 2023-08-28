package com.example.testcodewitharchitecture.user.controller;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.mock.TestClockHolder;
import com.example.testcodewitharchitecture.mock.TestContainer;
import com.example.testcodewitharchitecture.user.controller.response.MyProfileResponse;
import com.example.testcodewitharchitecture.user.controller.response.UserResponse;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserControllerTest {

    @Test
    void 사용자는_특정_유저의_정보를_개인정보는_소거된_전달_받을_수_있다() {
        TestContainer testContainer = TestContainer.create(null, null);

        testContainer.userRepository.save(User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build());

        ResponseEntity<UserResponse> response = testContainer.userController.getUserById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getNickname()).isEqualTo("abc");
        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 사용자는_존재하지_않는_유저의_아이디로_api_호출할_경우_404_응답을_받는다() {
        TestContainer testContainer = TestContainer.create(null, null);

        assertThatThrownBy(() -> testContainer.userController.getUserById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 사용자는_인증_코드로_계정을_활성화_시킬_수_있다() {
        TestContainer testContainer = TestContainer.create(null, null);

        testContainer.userRepository.save(User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.PENDING)
                .lastLoginAt(0L)
                .build());

        ResponseEntity<Void> response = testContainer.userController.verifyEmail(1L, "aaaaaaaa-aaaa-aaaa-aaaaaaaaaa");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(testContainer.userRepository.getById(1L).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 사용자는_인증_코드로_일치하지_않을_경우_권한_없음_에러를_내려준다() {
        TestContainer testContainer = TestContainer.create(null, null);

        testContainer.userRepository.save(User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.PENDING)
                .lastLoginAt(0L)
                .build());

        assertThatThrownBy(() -> testContainer.userController.verifyEmail(1L, "aaaaaaaa-aaaa-aaaa-aaaaaaaaab"))
                .isInstanceOf(CertificationCodeNotMatchedException.class);
    }

    @Test
    void 사용자는_내_정보를_불러올_때_개인정보인_주소도_갖고_올_수_있다() {
        TestContainer testContainer = TestContainer.create(new TestClockHolder(1000L), null);

        testContainer.userRepository.save(User.builder()
                .id(1L)
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build());

        ResponseEntity<MyProfileResponse> response = testContainer.userController.getMyInfo("abc@naver.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getNickname()).isEqualTo("abc");
        assertThat(response.getBody().getLastLoginAt()).isEqualTo(1000L);
        assertThat(response.getBody().getAddress()).isEqualTo("Seoul");
        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 사용자는_내_정보를_수정할_수_있다() {
        TestContainer testContainer = TestContainer.create(null, null);

        testContainer.userRepository.save(User.builder()
                .email("abc@naver.com")
                .nickname("abc")
                .address("Seoul")
                .certificationCode("aaaaaaaa-aaaa-aaaa-aaaaaaaaaa")
                .status(UserStatus.ACTIVE)
                .lastLoginAt(0L)
                .build());

        ResponseEntity<MyProfileResponse> response = testContainer.userController.updateMyInfo("abc@naver.com", UserUpdate.builder().address("Pang").nickname("abcdd").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("abc@naver.com");
        assertThat(response.getBody().getNickname()).isEqualTo("abcdd");
        assertThat(response.getBody().getAddress()).isEqualTo("Pang");
        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}