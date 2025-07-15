package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;

public record FetchChannelInviteCodeResponseRecord (
        UserId userId,
        ChannelId channelId,
        InviteCode inviteCode
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_CHANNEL_INVITECODE_RESPONSE;
    }
}
