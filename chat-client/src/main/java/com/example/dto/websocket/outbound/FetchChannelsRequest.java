package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class FetchChannelsRequest extends BaseRequest {

    public FetchChannelsRequest() {
        super(MessageType.FETCH_CHANNELS_REQUEST);
    }
}
