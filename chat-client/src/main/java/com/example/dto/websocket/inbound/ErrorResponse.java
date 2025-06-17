package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;

public class ErrorResponse extends BaseMessage {

    private final String messageType;
    private final String message;

    public ErrorResponse(String messageType, String message) {
        super(MessageType.ERROR);
        this.messageType = messageType;
        this.message = message;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }
}
