package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class KeepAliveRequest extends BaseRequest {

    public KeepAliveRequest() {
        super(MessageType.KEEP_ALIVE);
    }
}
