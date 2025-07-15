package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchConnectionsRequestRecord;
import com.example.dto.websocket.inbound.FetchConnectionsRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchConnectionsRequestHandler implements BaseRequestHandler<FetchConnectionsRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public FetchConnectionsRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchConnectionsRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        kafkaProducer.sendRequest(new FetchConnectionsRequestRecord(senderUserId, request.getStatus()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.FETCH_CONNECTIONS_REQUEST, "Fetch Connections failed.")));
    }
}
