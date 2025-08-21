package com.example.handler;

import com.example.dto.Message;
import com.example.service.TerminalService;
import com.example.util.JsonUtil;
import jakarta.websocket.Session;

public class WebSocketSender {
    private final TerminalService terminalService;

    public WebSocketSender(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    public void sendMessage(Session session, Message message) {
        if (session != null && session.isOpen()) {
            JsonUtil.toJson(message).ifPresent(msg -> {
                try {
                    session.getBasicRemote().sendText(msg);
                } catch (Exception ex) {
                    terminalService.printSystemMessage(String.format("%s send fail error : %s", msg, ex.getMessage()));
                }
            });
        }
    }
}
