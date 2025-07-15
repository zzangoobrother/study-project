package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.InviteRequestRecord;
import com.example.dto.websocket.inbound.InviteRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class InviterRequestHandler implements BaseRequestHandler<InviteRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public InviterRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, InviteRequest request) {
        UserId inviterUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new InviteRequestRecord(inviterUserId, request.getUserInviteCode()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.INVITE_REQUEST, "Invite failed.")));
    }
}
