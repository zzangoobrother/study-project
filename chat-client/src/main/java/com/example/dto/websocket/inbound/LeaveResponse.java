package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;

public class LeaveResponse extends BaseMessage {

    public LeaveResponse() {
        super(MessageType.LEAVE_RESPONSE);
    }
}
