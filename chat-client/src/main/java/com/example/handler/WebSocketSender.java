package com.example.handler;

import com.example.dto.websocket.outbound.WriteMessageRequest;
import com.example.service.TerminalService;
import com.example.util.JsonUtil;
import jakarta.websocket.Session;

public class WebSocketSender {

    private final TerminalService terminalService;

    public WebSocketSender(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    public void sendMessage(Session session, WriteMessageRequest message) {
        if (session != null && session.isOpen()) {
            JsonUtil.toJson(message).ifPresent(payload -> session.getAsyncRemote().sendText(payload, result -> {
                if (!result.isOK()) {
                    terminalService.printSystemMessage("'%s' send failed. cause : %s".formatted(payload, result.getException()));
                }
            }));
        }
    }
}
