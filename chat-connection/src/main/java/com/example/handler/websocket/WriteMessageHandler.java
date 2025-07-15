package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.WriteMessageRecord;
import com.example.dto.websocket.inbound.WriteMessage;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.MessageNotification;
import com.example.kafka.KafkaProducer;
import com.example.service.ClientNotificationService;
import com.example.service.MessageSeqIdGenerator;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WriteMessageHandler implements BaseRequestHandler<WriteMessage> {

    private final MessageSeqIdGenerator messageSeqIdGenerator;
    private final ClientNotificationService clientNotificationService;
    private final KafkaProducer kafkaProducer;

    public WriteMessageHandler(MessageSeqIdGenerator messageSeqIdGenerator, ClientNotificationService clientNotificationService, KafkaProducer kafkaProducer) {
        this.messageSeqIdGenerator = messageSeqIdGenerator;
        this.clientNotificationService = clientNotificationService;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, WriteMessage request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        ChannelId channelId = request.getChannelId();

        Runnable errorCallback = () -> clientNotificationService.sendErrorMessage(senderSession, new ErrorResponse(MessageType.WRITE_MESSAGE, "Write message failed."));

        messageSeqIdGenerator.getNext(channelId)
                .ifPresentOrElse(messageSeqId ->
                        kafkaProducer.sendMessageUsingPartitionKey(channelId, senderUserId, new WriteMessageRecord(senderUserId, request.getSerial(), channelId, request.getContent(), messageSeqId), errorCallback), errorCallback
                );
    }
}
