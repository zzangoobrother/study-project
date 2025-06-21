package com.example.dto.domain;

import java.util.Objects;

public record Channel(
        ChannelId channelId,
        String title,
        int headCount
) {

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Channel channel = (Channel) object;
        return Objects.equals(channelId, channel.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }
}
