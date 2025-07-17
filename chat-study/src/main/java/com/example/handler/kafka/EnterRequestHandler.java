package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.ChannelEntry;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.EnterRequestRecord;
import com.example.dto.kafka.EnterResponseRecord;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnterRequestHandler implements BaseRecordHandler<EnterRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public EnterRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(EnterRequestRecord record) {
        UserId senderUserId = record.userId();

        Pair<Optional<ChannelEntry>, ResultType> result = channelService.enter(record.channelId(), senderUserId);
        result.getFirst()
                .ifPresentOrElse(
                        channelEntry -> clientNotificationService.sendMessage(senderUserId, new EnterResponseRecord(senderUserId, record.channelId(), channelEntry.title(), channelEntry.lastReadMessageSeqId(), channelEntry.lastChannelMessageSeqId())),
                                () -> clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.ENTER_REQUEST, result.getSecond().getMessage()))
                );
    }
}
