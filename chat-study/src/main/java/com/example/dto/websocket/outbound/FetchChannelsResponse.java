package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.Channel;

import java.util.List;

public class FetchChannelsResponse extends BaseMessage {

    private final List<Channel> channels;

    public FetchChannelsResponse(List<Channel> channels) {
        super(MessageType.FETCH_CHANNELS_RESPONSE);
        this.channels = channels;
    }

    public List<Channel> getChannels() {
        return channels;
    }
}
