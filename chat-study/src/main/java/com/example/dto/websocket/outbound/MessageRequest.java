package com.example.dto.websocket.outbound;

import com.example.contants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageRequest extends BaseRequest {

    private final String username;
    private final String content;

    @JsonCreator
    public MessageRequest(@JsonProperty("username") String username, @JsonProperty("content") String content) {
        super(MessageType.MESSAGE);
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
