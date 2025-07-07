package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;

public class MessageNotification extends BaseMessage {

    private final ChannelId channelId;
    private final MessageSeqId messageSeqId;
    private final String username;
    private final String content;

    public MessageNotification(ChannelId channelId, MessageSeqId messageSeqId, String username, String content) {
        super(MessageType.NOTIFY_MESSAGE);
        this.channelId = channelId;
        this.messageSeqId = messageSeqId;
        this.username = username;
        this.content = content;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public MessageSeqId getMessageSeqId() {
        return messageSeqId;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }
}
