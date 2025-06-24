package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;

public class FetchChannelInviteCodeRequest extends BaseRequest {

    private final ChannelId channelId;

    public FetchChannelInviteCodeRequest(ChannelId channelId) {
        super(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST);
        this.channelId = channelId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }
}
