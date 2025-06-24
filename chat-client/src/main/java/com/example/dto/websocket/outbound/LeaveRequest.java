package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class LeaveRequest extends BaseRequest {

    public LeaveRequest() {
        super(MessageType.LEAVE_REQUEST);
    }
}
