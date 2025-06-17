package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class AcceptRequest extends BaseRequest {

    private final String username;

    public AcceptRequest(String username) {
        super(MessageType.ACCEPT_REQUEST);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
