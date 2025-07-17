package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.QuitRequestRecord;
import com.example.dto.kafka.QuitResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class QuitRequestHandler implements BaseRecordHandler<QuitRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public QuitRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(QuitRequestRecord record) {
        UserId senderUserId = record.userId();

        ResultType result;
        try {
            result = channelService.quit(record.channelId(), senderUserId);
        } catch (Exception ex) {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.QUIT_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        if (result == ResultType.SUCCESS) {
            clientNotificationService.sendMessage(senderUserId, new QuitResponseRecord(senderUserId, record.channelId()));
        } else {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.QUIT_REQUEST, result.getMessage()));
        }
    }
}
