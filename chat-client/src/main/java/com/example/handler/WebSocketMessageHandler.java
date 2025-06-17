package com.example.handler;

import com.example.dto.websocket.inbound.BaseMessage;
import com.example.service.TerminalService;
import com.example.util.JsonUtil;
import jakarta.websocket.MessageHandler;

public class WebSocketMessageHandler implements MessageHandler.Whole<String> {

    private final TerminalService terminalService;

    public WebSocketMessageHandler(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @Override
    public void onMessage(String payload) {
        JsonUtil.fromJson(payload, BaseMessage.class)
                .ifPresent(message -> terminalService.printMessage(message.username(), message.content()));
    }
}
