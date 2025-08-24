package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public record MessageRequest(
        String username,
        String content
) implements BaseRequest {
    @Override
    public MessageType getType() {
        return MessageType.MESSAGE;
    }
}
