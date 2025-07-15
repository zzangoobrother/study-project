package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;

public class LeaveRequest extends BaseRequest {

    @JsonCreator
    public LeaveRequest() {
        super(MessageType.LEAVE_REQUEST);
    }
}
