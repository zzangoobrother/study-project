package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.FetchMessageRequestRecord;
import com.example.dto.kafka.FetchMessageResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.MessageService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchMessagesRequestHandler implements BaseRecordHandler<FetchMessageRequestRecord> {

    private final MessageService messageService;
    private final ClientNotificationService clientNotificationService;

    public FetchMessagesRequestHandler(MessageService messageService, ClientNotificationService clientNotificationService) {
        this.messageService = messageService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchMessageRequestRecord record) {
        UserId senderUserId = record.userId();
        ChannelId channelId = record.channelId();
        Pair<List<Message>, ResultType> result = messageService.getMessages(channelId, record.startMessageSeqId(), record.endMessageSeqId());
        if (result.getSecond() == ResultType.SUCCESS) {
            List<Message> messages = result.getFirst();
            clientNotificationService.sendMessageUsingPartitionKey(channelId, senderUserId, new FetchMessageResponseRecord(senderUserId, channelId, messages));
        } else {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.FETCH_MESSAGES_REQUEST, result.getSecond().getMessage()));
        }
    }
}
