package com.example.handler.websocket;

import com.example.constants.Constants;
import com.example.dto.websocket.inbound.KeepAliveRequest;
import com.example.service.SessionService;
import org.springframework.web.socket.WebSocketSession;

public class KeepAliveRequestHandler implements BaseRequestHandler<KeepAliveRequest> {

    private final SessionService sessionService;

    public KeepAliveRequestHandler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, KeepAliveRequest request) {
        sessionService.refreshTTL((String) senderSession.getAttributes().get(Constants.HTTP_SESSION_ID.getValue()));
    }
}
