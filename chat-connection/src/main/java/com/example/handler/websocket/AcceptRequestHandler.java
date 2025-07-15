package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.AcceptRequestRecord;
import com.example.dto.websocket.inbound.AcceptRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AcceptRequestHandler implements BaseRequestHandler<AcceptRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public AcceptRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, AcceptRequest request) {
        UserId acceptorUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new AcceptRequestRecord(acceptorUserId, request.getUsername()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.ACCEPT_REQUEST, "Accept failed.")));
    }
}
