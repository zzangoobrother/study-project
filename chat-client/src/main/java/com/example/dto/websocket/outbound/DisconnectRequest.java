package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class DisconnectRequest extends BaseRequest {

    private final String username;

    public DisconnectRequest(String username) {
        super(MessageType.DISCONNECT_REQUEST);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
