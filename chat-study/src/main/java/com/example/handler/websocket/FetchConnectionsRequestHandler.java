package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.dto.domain.Connection;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchConnectionsRequest;
import com.example.dto.websocket.outbound.FetchConnectionsResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Component
public class FetchConnectionsRequestHandler implements BaseRequestHandler<FetchConnectionsRequest> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public FetchConnectionsRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchConnectionsRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        List<Connection> connections = userConnectionService.getUsersByStatus(senderUserId, request.getStatus()).stream()
                .map(user -> new Connection(user.username(), request.getStatus()))
                .toList();

        clientNotificationService.sendMessage(senderSession, senderUserId, new FetchConnectionsResponse(connections));
    }
}
