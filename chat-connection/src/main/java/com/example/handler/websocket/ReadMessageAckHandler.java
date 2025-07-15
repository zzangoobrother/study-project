package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ReadMessageAckRecord;
import com.example.dto.websocket.inbound.ReadMessageAck;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ReadMessageAckHandler implements BaseRequestHandler<ReadMessageAck> {

    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public ReadMessageAckHandler(ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, ReadMessageAck request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        ChannelId channelId = request.getChannelId();

        kafkaProducer.sendMessageUsingPartitionKey(channelId, senderUserId, new ReadMessageAckRecord(senderUserId, channelId, request.getMessageSeqId()), () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.READ_MESSAGES_ACK, "Read message ack failed.")));
    }
}
