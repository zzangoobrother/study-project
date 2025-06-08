package com.example.dto.websocket.outbound;

import com.example.contants.MessageType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageRequest.class, name = MessageType.MESSAGE),
        @JsonSubTypes.Type(value = KeepAliveRequest.class, name = MessageType.KEEP_ALIVE)
})
public abstract class BaseRequest {

    private final String type;

    public BaseRequest(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
