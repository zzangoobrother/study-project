package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class RejectRequest extends BaseRequest {

    private final String username;

    public RejectRequest(String username) {
        super(MessageType.REJECT_REQUEST);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
