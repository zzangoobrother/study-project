package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchChannelsRequestRecord;
import com.example.dto.websocket.inbound.FetchChannelsRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchChannelsRequestHandler implements BaseRequestHandler<FetchChannelsRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public FetchChannelsRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchChannelsRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new FetchChannelsRequestRecord(senderUserId), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.FETCH_CHANNELS_REQUEST, "Fetch Channel failed.")));
    }
}
