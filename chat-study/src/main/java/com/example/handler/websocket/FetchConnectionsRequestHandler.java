package com.example.handler.websocket;

import com.example.constants.Constants;
import com.example.dto.domain.Connection;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchConnectionsRequest;
import com.example.dto.websocket.outbound.FetchConnectionsResponse;
import com.example.service.UserConnectionService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Component
public class FetchConnectionsRequestHandler implements BaseRequestHandler<FetchConnectionsRequest> {

    private final UserConnectionService userConnectionService;
    private final WebSocketSessionManager webSocketSessionManager;

    public FetchConnectionsRequestHandler(UserConnectionService userConnectionService,
                                          WebSocketSessionManager webSocketSessionManager) {
        this.userConnectionService = userConnectionService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchConnectionsRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(Constants.USER_ID.getValue());
        List<Connection> connections = userConnectionService.getUsersByStatus(senderUserId, request.getStatus()).stream()
                .map(user -> new Connection(user.username(), request.getStatus()))
                .toList();

        webSocketSessionManager.sendMessage(senderSession, new FetchConnectionsResponse(connections));
    }
}
