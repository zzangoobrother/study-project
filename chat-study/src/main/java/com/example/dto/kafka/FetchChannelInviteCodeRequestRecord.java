package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;

public record FetchChannelInviteCodeRequestRecord (
        UserId userId,
        ChannelId channelId
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.FETCH_CHANNEL_INVITECODE_REQUEST;
    }
}
