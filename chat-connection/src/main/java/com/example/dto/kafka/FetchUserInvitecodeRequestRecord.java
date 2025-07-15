package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

public record FetchUserInvitecodeRequestRecord (
        UserId userId
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_USER_INVITECODE_REQUEST;
    }
}
