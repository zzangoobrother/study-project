package com.example.global.handler;

import com.example.domain.Message;
import com.example.dto.websocket.inbound.BaseRequest;
import com.example.dto.websocket.inbound.KeepAliveRequest;
import com.example.dto.websocket.inbound.MessageRequest;
import com.example.global.Constants.Constants;
import com.example.global.session.WebSocketSessionManager;
import com.example.repository.MessageRepository;
import com.example.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@RequiredArgsConstructor
@Slf4j
@Component
public class MessageHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionService sessionService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final MessageRepository messageRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ConcurrentWebSocketSessionDecorator concurrentWebSocketSessionDecorator = new ConcurrentWebSocketSessionDecorator(
                session, 5000, 100 * 1024);
        webSocketSessionManager.storeSession(concurrentWebSocketSessionDecorator);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        webSocketSessionManager.terminateSession(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        webSocketSessionManager.terminateSession(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession senderSession, TextMessage message) {
        String payload = message.getPayload();
        try {
            BaseRequest baseRequest = objectMapper.readValue(payload, BaseRequest.class);

            if (baseRequest instanceof MessageRequest messageRequest) {
                messageRepository.save(new Message(messageRequest.username(), messageRequest.content()));

                webSocketSessionManager.getSessions().forEach(participantSession -> {
                    if (!senderSession.getId().equals(participantSession.getId())) {
                        sendMessage(participantSession, messageRequest);
                    }
                });
            } else if (baseRequest instanceof KeepAliveRequest) {
                sessionService.refreshTTL((String) senderSession.getAttributes().get(Constants.HTTP_SESSION_ID.getValue()));
            }
        } catch (Exception ex) {
            log.error("message payload : {}, from : {}", payload, senderSession.getId());
            sendMessage(senderSession, new MessageRequest("system", "유효한 프로토콜이 아닙니다."));
        }
    }

    private void sendMessage(WebSocketSession session, MessageRequest MessageRequest) {
        try {
            String msg = objectMapper.writeValueAsString(MessageRequest);
            session.sendMessage(new TextMessage(msg));
        } catch (Exception ex) {
            log.error("메시지 전송 실패 error : {}", ex.getMessage());
        }
    }
}
