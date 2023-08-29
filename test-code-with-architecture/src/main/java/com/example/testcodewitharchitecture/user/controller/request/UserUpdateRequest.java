package com.example.testcodewitharchitecture.user.controller.request;

import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UserUpdateRequest {

    private final String nickname;
    private final String address;

    @Builder
    public UserUpdateRequest(@JsonProperty("nickname") String nickname, @JsonProperty("address") String address) {
        this.nickname = nickname;
        this.address = address;
    }

    public UserUpdate toUserUpdate() {
        return new UserUpdate(this.nickname, this.address);
    }
}
