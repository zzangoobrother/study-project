package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AcceptRequest extends BaseRequest {

    private final String username;

    @JsonCreator
    public AcceptRequest(@JsonProperty("userInviteCode") String username) {
        super(MessageType.ACCEPT_REQUEST);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
