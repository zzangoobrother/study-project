package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.KeepAlive;
import com.example.service.SessionService;
import org.springframework.web.socket.WebSocketSession;

public class KeepAliveHandler implements BaseRequestHandler<KeepAlive> {

    private final SessionService sessionService;

    public KeepAliveHandler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, KeepAlive request) {
        UserId senderUSerId = (UserId) senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        sessionService.refreshTTL(senderUSerId, (String) senderSession.getAttributes().get(IdKey.HTTP_SESSION_ID.getValue()));
    }
}
