package com.example.handler.websocket;

import com.example.dto.websocket.inbound.BaseRequest;
import org.springframework.web.socket.WebSocketSession;

public interface BaseRequestHandler<T extends BaseRequest> {
    void handleRequest(WebSocketSession webSocketSession, T request);
}
