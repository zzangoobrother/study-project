package com.example.dto.domain;

public record ChannelId(
        Long id
) {

    public ChannelId {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid ChannelId");
        }
    }
}
