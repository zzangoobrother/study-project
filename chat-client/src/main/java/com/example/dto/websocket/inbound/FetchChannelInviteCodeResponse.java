package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.InviteCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchChannelInviteCodeResponse extends BaseMessage {

    private final ChannelId channelId;
    private final InviteCode inviteCode;

    @JsonCreator
    public FetchChannelInviteCodeResponse(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("inviteCode") InviteCode inviteCode) {
        super(MessageType.FETCH_CHANNEL_INVITECODE_RESPONSE);
        this.channelId = channelId;
        this.inviteCode = inviteCode;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }
}
