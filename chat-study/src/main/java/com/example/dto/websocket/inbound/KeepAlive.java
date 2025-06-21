package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;

public class KeepAlive extends BaseRequest {

    @JsonCreator
    public KeepAlive() {
        super(MessageType.KEEP_ALIVE);
    }
}
