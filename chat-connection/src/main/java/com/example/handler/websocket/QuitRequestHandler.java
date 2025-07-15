package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.QuitRequestRecord;
import com.example.dto.websocket.inbound.QuitRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class QuitRequestHandler implements BaseRequestHandler<QuitRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public QuitRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, QuitRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new QuitRequestRecord(senderUserId, request.getChannelId()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.QUIT_REQUEST, "Quit failed.")));
    }
}
