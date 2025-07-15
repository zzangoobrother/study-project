package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;

public record JoinRequestRecord (
        UserId userId,
        InviteCode inviteCode
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.JOIN_REQUEST;
    }
}
