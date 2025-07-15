package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RejectRequest extends BaseRequest {

    private final String username;

    @JsonCreator
    public RejectRequest(@JsonProperty("username") String username) {
        super(MessageType.REJECT_REQUEST);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
