package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;

public class EnterRequest extends BaseRequest {

    private final ChannelId channelId;

    public EnterRequest(ChannelId channelId) {
        super(MessageType.ENTER_REQUEST);
        this.channelId = channelId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }
}
