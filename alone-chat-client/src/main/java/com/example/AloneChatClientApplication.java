package com.example;

import com.example.dto.websocket.outbound.MessageRequest;
import com.example.handler.CommandHandler;
import com.example.handler.WebSocketMessageHandler;
import com.example.handler.WebSocketSender;
import com.example.service.RestApiService;
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

        RestApiService restApiService = new RestApiService(terminalService, WEBSOCKET_BASE_URL);
        WebSocketSender webSocketSender = new WebSocketSender(terminalService);
        WebSocketService webSocketService = new WebSocketService(terminalService, webSocketSender, WEBSOCKET_BASE_URL, WEBSOCKET_ENDPOINT);
        webSocketService.setWebSocketMessageHandler(new WebSocketMessageHandler(terminalService));

        CommandHandler commandHandler = new CommandHandler(restApiService, webSocketService, terminalService);

        while (true) {
            String input = terminalService.readLine("Enter message : ");
            if (!input.isEmpty() && input.charAt(0) == '/') {
                String[] parts = input.split(" ", 2);
                String command = parts[0].substring(1);
                String argument = parts.length > 1 ? parts[1] : "";
                if (!commandHandler.process(command, argument)) {
                    break;
                }
            } else if (!input.isEmpty()) {
                terminalService.printMessage("<me>", input);
                webSocketService.sendMessage(new MessageRequest("test client", input));
            }
        }
    }
}
