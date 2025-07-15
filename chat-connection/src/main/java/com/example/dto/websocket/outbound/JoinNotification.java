package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;

public class JoinNotification extends BaseMessage {

    private final ChannelId channelId;
    private final String title;

    public JoinNotification(ChannelId channelId, String title) {
        super(MessageType.NOTIFY_JOIN);
        this.channelId = channelId;
        this.title = title;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public String getTitle() {
        return title;
    }
}
