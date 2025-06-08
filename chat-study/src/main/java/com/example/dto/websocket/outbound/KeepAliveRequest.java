package com.example.dto.websocket.outbound;

import com.example.contants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;

public class KeepAliveRequest extends BaseRequest {

    @JsonCreator
    public KeepAliveRequest() {
        super(MessageType.KEEP_ALIVE);
    }
}
