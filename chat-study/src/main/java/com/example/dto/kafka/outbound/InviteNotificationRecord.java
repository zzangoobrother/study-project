package com.example.dto.kafka.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

public record InviteNotificationRecord (
        UserId userId,
        String username
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.ASK_INVITE;
    }
}
