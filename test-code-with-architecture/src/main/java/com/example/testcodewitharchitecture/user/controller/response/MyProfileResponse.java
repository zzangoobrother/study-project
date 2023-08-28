package com.example.testcodewitharchitecture.user.controller.response;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyProfileResponse {

    private Long id;
    private String email;
    private String nickname;
    private String address;
    private UserStatus status;
    private Long lastLoginAt;

    public static MyProfileResponse from(User user) {
        return MyProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .status(user.getStatus())
                .address(user.getAddress())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
