package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.Channel;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.JoinRequestRecord;
import com.example.dto.kafka.JoinResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JoinRequestHandler implements BaseRecordHandler<JoinRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public JoinRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(JoinRequestRecord record) {
        UserId senderUserId = record.userId();

        Pair<Optional<Channel>, ResultType> result;
        try {
            result = channelService.join(record.inviteCode(), senderUserId);
        } catch (Exception ex) {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.JOIN_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        result.getFirst().ifPresentOrElse(
                channel -> clientNotificationService.sendMessage(senderUserId, new JoinResponseRecord(senderUserId, channel.channelId(), channel.title())),
                () -> clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.JOIN_REQUEST, result.getSecond().getMessage()))
        );
    }
}
