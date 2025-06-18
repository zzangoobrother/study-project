package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DisconnectResponse extends BaseMessage {

    private final String username;
    private final UserConnectionStatus status;

    @JsonCreator
    public DisconnectResponse(@JsonProperty("username") String username, @JsonProperty("status") UserConnectionStatus status) {
        super(MessageType.DISCONNECT_RESPONSE);
        this.username = username;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public UserConnectionStatus getStatus() {
        return status;
    }
}
