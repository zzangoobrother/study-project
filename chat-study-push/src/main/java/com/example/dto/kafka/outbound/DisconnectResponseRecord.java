package com.example.dto.kafka.outbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;

public record DisconnectResponseRecord (
        UserId userId,
        String username,
        UserConnectionStatus status
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.DISCONNECT_RESPONSE;
    }
}
