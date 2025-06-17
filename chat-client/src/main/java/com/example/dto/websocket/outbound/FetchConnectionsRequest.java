package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;

public class FetchConnectionsRequest extends BaseRequest {

    private final UserConnectionStatus status;

    public FetchConnectionsRequest(UserConnectionStatus status) {
        super(MessageType.FETCH_CONNECTIONS_REQUEST);
        this.status = status;
    }

    public UserConnectionStatus getStatus() {
        return status;
    }
}
