package com.example.service;

import com.example.dto.Message;
import com.example.handler.WebSocketMessageHandler;
import com.example.handler.WebSocketSender;
import com.example.handler.WebSocketSessionHandler;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;

import java.net.URI;

public class WebSocketService {
    private final TerminalService terminalService;
    private final WebSocketSender webSocketSender;
    private final String webSocketUrl;
    private WebSocketMessageHandler webSocketMessageHandler;
    private Session session;

    public WebSocketService(TerminalService terminalService, WebSocketSender webSocketSender, String url, String endpoint) {
        this.terminalService = terminalService;
        this.webSocketSender = webSocketSender;
        this.webSocketUrl = "ws://" + url + endpoint;
    }

    public boolean createSession() {
        ClientManager clientManager = ClientManager.createClient();

        try {
            session = clientManager.connectToServer(new WebSocketSessionHandler(terminalService), new URI(webSocketUrl));
            session.addMessageHandler(webSocketMessageHandler);
            return true;
        } catch (Exception ex) {
            terminalService.printSystemMessage(String.format("faile to connect to : {}, error : {}", webSocketUrl, ex.getMessage()));
            return false;
        }
    }

    public void closeSession() {
        try {
            if (session != null) {
                if (session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "NORMAL CLOSURE"));
                }

                session = null;
            }
        } catch (Exception ex) {
            terminalService.printSystemMessage(String.format("fail to close error : %S", ex.getMessage()));
        }
    }

    public void sendMessage(Message message) {
        if (session != null && session.isOpen()) {
            webSocketSender.sendMessage(session, message);
        } else {
            terminalService.printSystemMessage("fail to send message. Session is not open");
        }
    }

    public void setWebSocketMessageHandler(WebSocketMessageHandler webSocketMessageHandler) {
        this.webSocketMessageHandler = webSocketMessageHandler;
    }
}
