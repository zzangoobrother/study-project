package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.Connection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FetchConnectionsResponse extends BaseMessage {

    private final List<Connection> connections;

    @JsonCreator
    public FetchConnectionsResponse(@JsonProperty("connections") List<Connection> connections) {
        super(MessageType.FETCH_CONNECTIONS_RESPONSE);
        this.connections = connections;
    }

    public List<Connection> getConnections() {
        return connections;
    }
}
