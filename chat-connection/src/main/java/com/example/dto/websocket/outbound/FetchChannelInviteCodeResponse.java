package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.InviteCode;

public class FetchChannelInviteCodeResponse extends BaseMessage {

    private final ChannelId channelId;
    private final InviteCode inviteCode;

    public FetchChannelInviteCodeResponse(ChannelId channelId, InviteCode inviteCode) {
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
