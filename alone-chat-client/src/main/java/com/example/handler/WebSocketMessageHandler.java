package com.example.handler;

import com.example.dto.Message;
import com.example.service.TerminalService;
import com.example.util.JsonUtil;
import jakarta.websocket.MessageHandler;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WebSocketMessageHandler implements MessageHandler.Whole<String> {
    private final TerminalService terminalService;

    @Override
    public void onMessage(String payload) {
        JsonUtil.fromJson(payload, Message.class)
                .ifPresent(msg -> terminalService.printMessage(msg.username(), msg.content()));
    }
}
