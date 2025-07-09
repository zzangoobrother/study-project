package com.example.dto.domain;

public record ChannelEntry(
        String title,
        MessageSeqId lastReadMessageSeqId,
        MessageSeqId lastChannelMessageSeqId
) {
}
