package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;

public class KeepAliveRequest extends BaseRequest {

    @JsonCreator
    public KeepAliveRequest() {
        super(MessageType.KEEP_ALIVE);
    }
}
