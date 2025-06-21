package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WriteMessage extends BaseRequest {

    private final String username;
    private final String content;

    @JsonCreator
    public WriteMessage(@JsonProperty("username") String username, @JsonProperty("content") String content) {
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
