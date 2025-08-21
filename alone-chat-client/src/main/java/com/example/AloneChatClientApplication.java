package com.example;

import com.example.handler.WebSocketSender;
import com.example.service.TerminalService;
import com.example.service.WebSocketService;

import java.io.IOException;

public class AloneChatClientApplication {

    public static void main(String[] args) {
        String WEBSOCKET_BASE_URL = "localhost:8080";
        String WEBSOCKET_ENDPOINT = "/ws/v1/message";

        TerminalService terminalService;
        try {
            terminalService = TerminalService.create();
        } catch (IOException e) {
            return;
        }

        WebSocketSender webSocketSender = new WebSocketSender(terminalService);
        WebSocketService webSocketService = new WebSocketService(terminalService, webSocketSender, WEBSOCKET_BASE_URL, WEBSOCKET_ENDPOINT);
    }
}
