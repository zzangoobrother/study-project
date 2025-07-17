package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

public record DisconnectRequestRecord (
        UserId userId,
        String username
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.DISCONNECT_REQUEST;
    }
}
