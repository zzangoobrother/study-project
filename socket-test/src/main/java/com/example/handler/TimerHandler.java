package com.example.handler;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TimerHandler.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("ConnectionEstablished {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("TransportError : [{}] from {}", exception.getMessage(), session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) {
        log.info("ConnectionClosed : [{}] from {}", status, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @Nonnull TextMessage message) throws Exception {
        log.info("Received TextMessage : [{}] from {}", message, session.getId());

        try {
            long seconds = Long.parseLong(message.getPayload());
            long timestamp = Instant.now().toEpochMilli();
            scheduledExecutorService.schedule(() -> sendMessage(session, String.format("%d에 등록한 %d초 타이머 완료.", timestamp, seconds)), seconds, TimeUnit.SECONDS);

            sendMessage(session, String.format("%d에 등록한 %d초 타이머 등록 완료.", timestamp, seconds));
        } catch (Exception ex) {
            sendMessage(session, "정수가 아님. 타이머 등록 실패.");
        }
    }

    private void sendMessage(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
            log.info("send message: {} to {}", message, session.getId());
        } catch (Exception ex) {
            log.error("메시지 전송이 실패 to {} error: {}", session.getId(), ex.getMessage());
        }
    }
}
