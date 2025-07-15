package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.Connection;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.outbound.BaseMessage;

import java.util.List;

public record FetchConnectionsResponseRecord (
        UserId userId,
        List<Connection> connections
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_CONNECTIONS_RESPONSE;
    }
}
