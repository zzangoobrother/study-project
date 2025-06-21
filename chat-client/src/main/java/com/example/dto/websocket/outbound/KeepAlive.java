package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class KeepAlive extends BaseRequest {

    public KeepAlive() {
        super(MessageType.KEEP_ALIVE);
    }
}
