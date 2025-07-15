package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.LeaveRequestRecord;
import com.example.dto.websocket.inbound.LeaveRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class LeaveRequestHandler implements BaseRequestHandler<LeaveRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public LeaveRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, LeaveRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new LeaveRequestRecord(senderUserId), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.LEAVE_REQUEST, "Leave failed.")));
    }
}
