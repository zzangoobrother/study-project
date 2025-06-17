package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class WriteMessageRequest extends BaseRequest {

    private final String username;
    private final String content;

    public WriteMessageRequest(String username, String content) {
        super(MessageType.WRITE_MESSAGE);
        this.username = username;
        this.content = content;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }
}
