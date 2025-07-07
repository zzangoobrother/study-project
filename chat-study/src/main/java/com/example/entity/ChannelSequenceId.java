package com.example.entity;

import java.io.Serializable;
import java.util.Objects;

public class ChannelSequenceId implements Serializable {

    private Long channelId;
    private Long messageSequence;

    public ChannelSequenceId() {}

    public ChannelSequenceId(Long channelId, Long messageSequence) {
        this.channelId = channelId;
        this.messageSequence = messageSequence;
    }

    public Long getMessageSequence() {
        return messageSequence;
    }

    public Long getChannelId() {
        return channelId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ChannelSequenceId that = (ChannelSequenceId) object;
        return Objects.equals(channelId, that.channelId) && Objects.equals(messageSequence, that.messageSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, messageSequence);
    }

    @Override
    public String toString() {
        return "ChannelSequenceId{" +
                "channelId=" + channelId +
                ", messageSequence=" + messageSequence +
                '}';
    }
}
