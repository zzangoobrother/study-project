package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.DisconnectRequestRecord;
import com.example.dto.websocket.inbound.DisconnectRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DisconnectRequestHandler implements BaseRequestHandler<DisconnectRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public DisconnectRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, DisconnectRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new DisconnectRequestRecord(senderUserId, request.getUsername()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.DISCONNECT_REQUEST, "Disconnect failed.")));
    }
}
