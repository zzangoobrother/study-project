package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;

public record InviteRequestRecord (
        UserId userId,
        InviteCode userInviteCode
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.INVITE_REQUEST;
    }
}
