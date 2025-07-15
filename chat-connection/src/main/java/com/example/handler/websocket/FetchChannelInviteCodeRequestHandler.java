package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchChannelInviteCodeRequestRecord;
import com.example.dto.websocket.inbound.FetchChannelInviteCodeRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchChannelInviteCodeRequestHandler implements BaseRequestHandler<FetchChannelInviteCodeRequest
        > {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public FetchChannelInviteCodeRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchChannelInviteCodeRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new FetchChannelInviteCodeRequestRecord(senderUserId, request.getChannelId()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Fetch Channel Invitecode failed.")));
    }
}
