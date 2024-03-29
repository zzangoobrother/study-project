package com.example.testcodewitharchitecture.user.controller.response;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @Test
    void User으로_응답을_생성할_수_있다() {
        User user = User.builder()
                .email("abc@naver.com")
                .nickname("abc")
                .status(UserStatus.ACTIVE)
                .build();

        UserResponse response = UserResponse.from(user);

        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getNickname()).isEqualTo(user.getNickname());
        assertThat(response.getStatus()).isEqualTo(user.getStatus());
    }
}