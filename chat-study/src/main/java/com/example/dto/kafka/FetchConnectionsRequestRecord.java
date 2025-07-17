package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;

public record FetchConnectionsRequestRecord (
        UserId userId,
        UserConnectionStatus status
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_CONNECTIONS_REQUEST;
    }
}
