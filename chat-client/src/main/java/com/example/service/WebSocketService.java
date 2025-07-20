package com.example.service;

import com.example.dto.ServerEndpoint;
import com.example.dto.websocket.outbound.BaseRequest;
import com.example.dto.websocket.outbound.KeepAlive;
import com.example.dto.websocket.outbound.WriteMessage;
import com.example.handler.WebSocketMessageHandler;
import com.example.handler.WebSocketSessionHandler;
import com.example.util.JsonUtil;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocketService {

    private final UserService userService;
    private final TerminalService terminalService;
    private final MessageService messageService;
    private final List<ServerEndpoint> serverEndpoints;
    private final String webSocketEndpoint;
    private WebSocketMessageHandler webSocketMessageHandler;
    private Session session;
    private ScheduledExecutorService scheduledExecutorService = null;

    public WebSocketService(UserService userService, TerminalService terminalService, MessageService messageService, List<ServerEndpoint> serverEndpoints, String webSocketEndpoint) {
        this.userService = userService;
        this.terminalService = terminalService;
        this.messageService = messageService;
        this.serverEndpoints = serverEndpoints;
        this.webSocketEndpoint = webSocketEndpoint;
    }

    public void setWebSocketMessageHandler(WebSocketMessageHandler webSocketMessageHandler) {
        this.webSocketMessageHandler = webSocketMessageHandler;
    }

    public boolean createSession(String sessionId) {
        ClientManager clientManager = ClientManager.createClient();

        ClientEndpointConfig.Configurator configurator = new ClientEndpointConfig.Configurator() {
            @Override
            public void beforeRequest(Map<String, List<String>> headers) {
                headers.put("Cookie", List.of("SESSION=" + sessionId));
            }
        };

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().configurator(configurator).build();

        for (ServerEndpoint serverEndpoint : serverEndpoints) {
            try {
                session = connect(clientManager, config, "ws://%s:%s%s".formatted(serverEndpoint.address(), serverEndpoint.port(), webSocketEndpoint));
                return true;
            } catch (Exception ex) {
                terminalService.printSystemMessage("Retry with the next server node.");
            }
        }

        terminalService.printSystemMessage("Connect failed.");
        return false;
    }

    private Session connect(ClientManager clientManager, ClientEndpointConfig config, String webSocketUrl) throws Exception {
        try {
            terminalService.printSystemMessage("Connect to url : %s".formatted(webSocketUrl));
            session = clientManager.connectToServer(new WebSocketSessionHandler(userService, terminalService, this), config, new URI(webSocketUrl));
            session.addMessageHandler(webSocketMessageHandler);
            enableKeepAlive();
            return session;
        } catch (Exception ex) {
            terminalService.printSystemMessage("Failed to connect to [%s] error : %s".formatted(webSocketUrl, ex.getMessage()));
            throw ex;
        }
    }

    public void closeSession() {
        try {
            disableKeepAlive();
            if (session != null) {
                if (session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "NORMAL CLOSURE"));
                }

                session = null;
            }
        } catch (Exception ex) {
            terminalService.printSystemMessage("Failed to close error : %s".formatted(ex.getMessage()));
        }
    }

    public void sendMessage(BaseRequest baseRequest) {
        if (session != null && session.isOpen()) {
            if (baseRequest instanceof WriteMessage messageRequest) {
                messageService.sendMessage(session, messageRequest);
                return;
            }

            JsonUtil.toJson(baseRequest).ifPresent(payload -> session.getAsyncRemote().sendText(payload, result -> {
                if (!result.isOK()) {
                    terminalService.printSystemMessage("%s send failed. cause : %s".formatted(payload, result.getException()));
                }
            }));
        } else {
            terminalService.printSystemMessage("Failed to send message. Session is not open");
        }
    }

    private void enableKeepAlive() {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        }

        scheduledExecutorService.scheduleAtFixedRate(() -> sendMessage(new KeepAlive()), 1, 1, TimeUnit.MINUTES);
    }

    private void disableKeepAlive() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }
}
