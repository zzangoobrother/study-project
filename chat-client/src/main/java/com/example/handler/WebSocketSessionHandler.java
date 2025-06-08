package com.example.handler;

import com.example.service.TerminalService;
import com.example.service.WebSocketService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

public class WebSocketSessionHandler extends Endpoint {

    private final TerminalService terminalService;
    private final WebSocketService webSocketService;

    public WebSocketSessionHandler(TerminalService terminalService, WebSocketService webSocketService) {
        this.terminalService = terminalService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        terminalService.printSystemMessage("WebSocket connected");
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        webSocketService.closeSession();
        terminalService.printSystemMessage("WebSocket closed CloseReason : " + closeReason.getReasonPhrase());
    }

    @Override
    public void onError(Session session, Throwable thr) {
        terminalService.printSystemMessage("WebSocket Error : " + thr.getMessage());
    }
}
