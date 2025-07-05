package com.example.dto.kafka.outbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;

public record InviteResponseRecord (
        UserId userId, InviteCode inviteCode, UserConnectionStatus status
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.INVITE_RESPONSE;
    }
}
