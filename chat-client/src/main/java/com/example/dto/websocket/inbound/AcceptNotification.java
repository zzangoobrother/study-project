package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AcceptNotification extends BaseMessage {

    private final String username;

    @JsonCreator
    public AcceptNotification(@JsonProperty("username") String username) {
        super(MessageType.NOTIFY_ACCEPT);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
