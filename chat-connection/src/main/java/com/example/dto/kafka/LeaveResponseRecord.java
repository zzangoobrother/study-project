package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

public record LeaveResponseRecord (
        UserId userId
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.LEAVE_RESPONSE;
    }
}
