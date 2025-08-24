package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public record KeepAliveRequest(

) implements BaseRequest {
    @Override
    public MessageType getType() {
        return MessageType.KEEP_ALIVE;
    }
}
