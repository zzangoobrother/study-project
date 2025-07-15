package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchMessageRequestRecord;
import com.example.dto.websocket.inbound.FetchMessageRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchMessagesRequestHandler implements BaseRequestHandler<FetchMessageRequest> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public FetchMessagesRequestHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchMessageRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        ChannelId channelId = request.getChannelId();

        kafkaProducer.sendMessageUsingPartitionKey(channelId, senderUserId, new FetchMessageRequestRecord(senderUserId, channelId, request.getStartMessageSeqId(), request.getEndMessageSeqId()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.FETCH_MESSAGES_REQUEST, "Fetch message failed.")));
    }
}
