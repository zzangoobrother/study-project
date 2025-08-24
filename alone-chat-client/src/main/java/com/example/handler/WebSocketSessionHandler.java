package com.example.handler;

import com.example.service.TerminalService;
import com.example.service.WebSocketService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WebSocketSessionHandler extends Endpoint {
    private final TerminalService terminalService;
    private final WebSocketService webSocketService;

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        terminalService.printSystemMessage("Websocket connected");
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        webSocketService.closeSession();
        terminalService.printSystemMessage("Websocket closed reason : " + closeReason.getReasonPhrase());
    }

    @Override
    public void onError(Session session, Throwable thr) {
        terminalService.printSystemMessage("Websocket error : " + thr.getMessage());
    }
}
