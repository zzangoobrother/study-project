package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchConnectionsRequest extends BaseRequest {

    private final UserConnectionStatus status;

    @JsonCreator
    public FetchConnectionsRequest(@JsonProperty("status") UserConnectionStatus status) {
        super(MessageType.FETCH_CONNECTIONS_REQUEST);
        this.status = status;
    }

    public UserConnectionStatus getStatus() {
        return status;
    }
}
