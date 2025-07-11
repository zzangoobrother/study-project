package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EnterResponse extends BaseMessage {

    private final ChannelId channelId;
    private final String title;
    private final MessageSeqId lastReadMessageSeqId;
    private final MessageSeqId lastChannelMessageSeqId;

    @JsonCreator
    public EnterResponse(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("title") String title, @JsonProperty("lastReadMessageSeqId") MessageSeqId lastReadMessageSeqId, @JsonProperty("lastChannelMessageSeqId") MessageSeqId lastChannelMessageSeqId) {
        super(MessageType.ENTER_RESPONSE);
        this.channelId = channelId;
        this.title = title;
        this.lastReadMessageSeqId = lastReadMessageSeqId;
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
