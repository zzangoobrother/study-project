package com.example;

import com.example.dto.websocket.outbound.WriteMessage;
import com.example.handler.CommandHandler;
import com.example.handler.InboundMessageHandler;
import com.example.handler.WebSocketMessageHandler;
import com.example.handler.WebSocketSender;
import com.example.service.RestApiService;
import com.example.service.TerminalService;
import com.example.service.UserService;
import com.example.service.WebSocketService;
import org.jline.reader.UserInterruptException;

import java.io.IOException;

public class ChatClientApplication {

    public static void main(String[] args) {
        final String BASE_URL = "localhost:80";
        final String WEBSOCKET_ENDPOINT = "/ws/v1/message";

        TerminalService terminalService;
        try {
            terminalService = TerminalService.create();
        } catch (IOException e) {
            System.err.println("Failed to run ChatClient");
            return;
        }

        UserService userService = new UserService();
        InboundMessageHandler inboundMessageHandler = new InboundMessageHandler(terminalService, userService);
        RestApiService restApiService = new RestApiService(terminalService, BASE_URL);
        WebSocketSender webSocketSender = new WebSocketSender(terminalService);
        WebSocketService webSocketService = new WebSocketService(userService, terminalService, webSocketSender, BASE_URL, WEBSOCKET_ENDPOINT);
        webSocketService.setWebSocketMessageHandler(new WebSocketMessageHandler(inboundMessageHandler));
        CommandHandler commandHandler = new CommandHandler(userService, restApiService, webSocketService, terminalService);

        terminalService.printSystemMessage("'/help' Help for command. ex : /help");

        while (true) {
            try {
                String input = terminalService.readLine("Enter message : ");
                if (!input.isEmpty() && input.charAt(0) == '/') {
                    String[] parts = input.split(" ", 2);
                    String command = parts[0].substring(1);
                    String argument = parts.length > 1 ? parts[1] : "";
                    if (!commandHandler.process(command, argument)) {
                        break;
                    }
                } else if (!input.isEmpty() && userService.isInChannel()) {
                    terminalService.printMessage("<me>", input);
                    webSocketService.sendMessage(new WriteMessage(userService.getChannelId(), input));
                }
            } catch (UserInterruptException ex) {
                terminalService.flush();
                commandHandler.process("exit", "");
                return;
            } catch (NumberFormatException ex) {
                terminalService.printSystemMessage("Invalid Input : " + ex.getMessage());
            }
        }
    }
}
