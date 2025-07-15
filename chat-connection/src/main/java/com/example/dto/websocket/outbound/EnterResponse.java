package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;

public class EnterResponse extends BaseMessage {

    private final ChannelId channelId;
    private final String title;
    private final MessageSeqId lastReadMessageSeqId;
    private final MessageSeqId lastChannelMessageSeqId;

    public EnterResponse(ChannelId channelId, String title, MessageSeqId lastReadMessageSeqId, MessageSeqId lastChannelMessageSeqId) {
        super(MessageType.ENTER_RESPONSE);
        this.channelId = channelId;
        this.title = title;
        this.lastReadMessageSeqId  = lastReadMessageSeqId;
        this.lastChannelMessageSeqId = lastChannelMessageSeqId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public String getTitle() {
        return title;
    }

    public MessageSeqId getLastReadMessageSeqId() {
        return lastReadMessageSeqId;
    }

    public MessageSeqId getLastChannelMessageSeqId() {
        return lastChannelMessageSeqId;
    }
}
