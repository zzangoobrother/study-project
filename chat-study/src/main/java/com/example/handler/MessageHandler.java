package com.example.handler;

import com.example.constants.Constants;
import com.example.dto.domain.Message;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.BaseRequest;
import com.example.dto.websocket.inbound.KeepAliveRequest;
import com.example.dto.websocket.inbound.WriteMessageRequest;
import com.example.entity.MessageEntity;
import com.example.repository.MessageRepository;
import com.example.service.SessionService;
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
public class MessageHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final JsonUtil jsonUtil;
    private final SessionService sessionService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final MessageRepository messageRepository;

    public MessageHandler(JsonUtil jsonUtil, SessionService sessionService, WebSocketSessionManager webSocketSessionManager, MessageRepository messageRepository) {
        this.jsonUtil = jsonUtil;
        this.sessionService = sessionService;
        this.webSocketSessionManager = webSocketSessionManager;
        this.messageRepository = messageRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("ConnectionEstablished : {}", session.getId());

        ConcurrentWebSocketSessionDecorator concurrentWebSocketSessionDecorator = new ConcurrentWebSocketSessionDecorator(session, 5000, 100 * 1024);
        UserId userId = (UserId) session.getAttributes().get(Constants.USER_ID.getValue());
        webSocketSessionManager.putSession(userId, concurrentWebSocketSessionDecorator);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("TransportError : [{}] from {}", exception.getMessage(), session.getId());
        UserId userId = (UserId) session.getAttributes().get(Constants.USER_ID.getValue());
        webSocketSessionManager.closeSession(userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) throws Exception {
        log.info("ConnectionEstablished : [{}] from {}", status, session.getId());
        UserId userId = (UserId) session.getAttributes().get(Constants.USER_ID.getValue());
        webSocketSessionManager.closeSession(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession senderSession, @Nonnull TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received TextMessage : [{}] from {}", payload, senderSession.getId());
        try {
            BaseRequest baseRequest = jsonUtil.fromJson(payload, BaseRequest.class).get();

            if (baseRequest instanceof WriteMessageRequest messageRequest) {
                Message receivedMessage = new Message(messageRequest.getUsername(), messageRequest.getContent());
                messageRepository.save(new MessageEntity(receivedMessage.username(), receivedMessage.content()));

                webSocketSessionManager.getSessions().forEach(participantSession -> {
                    if (!senderSession.getId().equals(participantSession.getId())) {
                        sendMessage(participantSession, receivedMessage);
                    }
                });
            } else if (baseRequest instanceof KeepAliveRequest) {
                sessionService.refreshTTL((String) senderSession.getAttributes().get(Constants.HTTP_SESSION_ID.getValue()));
            }
        } catch (Exception ex) {
            String errorMessage = "유효한 프로토콜이 아닙니다.";
            log.error("errorMessage payload : {} from {}", payload, senderSession.getId());
            sendMessage(senderSession, new Message("system", errorMessage));
        }
    }
}
