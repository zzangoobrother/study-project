package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;

public class QuitRequest extends BaseRequest {

    private final ChannelId channelId;

    public QuitRequest(ChannelId channelId) {
        super(MessageType.QUIT_REQUEST);
        this.channelId = channelId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }
}
