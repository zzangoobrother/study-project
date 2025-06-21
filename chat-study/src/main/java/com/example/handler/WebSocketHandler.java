package com.example.handler;

import com.example.constants.IdKey;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.BaseRequest;
import com.example.handler.websocket.RequestDispatcher;
import com.example.session.WebSocketSessionManager;
import com.example.util.JsonUtil;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private final JsonUtil jsonUtil;
    private final WebSocketSessionManager webSocketSessionManager;
    private final RequestDispatcher requestDispatcher;

    public WebSocketHandler(JsonUtil jsonUtil, WebSocketSessionManager webSocketSessionManager, RequestDispatcher requestDispatcher) {
        this.jsonUtil = jsonUtil;
        this.webSocketSessionManager = webSocketSessionManager;
        this.requestDispatcher = requestDispatcher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("ConnectionEstablished : {}", session.getId());

        ConcurrentWebSocketSessionDecorator concurrentWebSocketSessionDecorator = new ConcurrentWebSocketSessionDecorator(session, 5000, 100 * 1024);
        UserId userId = (UserId) session.getAttributes().get(IdKey.USER_ID.getValue());
        webSocketSessionManager.putSession(userId, concurrentWebSocketSessionDecorator);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("TransportError : [{}] from {}", exception.getMessage(), session.getId());
        UserId userId = (UserId) session.getAttributes().get(IdKey.USER_ID.getValue());
        webSocketSessionManager.closeSession(userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) throws Exception {
        log.info("ConnectionEstablished : [{}] from {}", status, session.getId());
        UserId userId = (UserId) session.getAttributes().get(IdKey.USER_ID.getValue());
        webSocketSessionManager.closeSession(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession senderSession, @Nonnull TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received TextMessage : [{}] from {}", payload, senderSession.getId());
        jsonUtil.fromJson(payload, BaseRequest.class).ifPresent(baseRequest -> requestDispatcher.dispatchRequest(senderSession, baseRequest));
    }
}
