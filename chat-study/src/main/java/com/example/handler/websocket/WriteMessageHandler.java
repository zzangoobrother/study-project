package com.example.handler.websocket;

import com.example.dto.websocket.inbound.WriteMessage;
import com.example.dto.websocket.outbound.MessageNotification;
import com.example.entity.MessageEntity;
import com.example.repository.MessageRepository;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WriteMessageHandler implements BaseRequestHandler<WriteMessage> {

    private final WebSocketSessionManager webSocketSessionManager;
    private final MessageRepository messageRepository;

    public WriteMessageHandler(WebSocketSessionManager webSocketSessionManager, MessageRepository messageRepository) {
        this.webSocketSessionManager = webSocketSessionManager;
        this.messageRepository = messageRepository;
    }


    @Override
    public void handleRequest(WebSocketSession senderSession, WriteMessage request) {
        MessageNotification receivedMessage = new MessageNotification(request.getUsername(), request.getContent());
        messageRepository.save(new MessageEntity(receivedMessage.getUsername(), receivedMessage.getContent()));

        webSocketSessionManager.getSessions().forEach(participantSession -> {
            if (!senderSession.getId().equals(participantSession.getId())) {
                webSocketSessionManager.sendMessage(participantSession, receivedMessage);
            }
        });
    }
}
