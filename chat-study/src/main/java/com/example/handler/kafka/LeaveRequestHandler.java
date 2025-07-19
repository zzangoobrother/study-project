package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.LeaveRequestRecord;
import com.example.dto.kafka.LeaveResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class LeaveRequestHandler implements BaseRecordHandler<LeaveRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public LeaveRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(LeaveRequestRecord record) {
        UserId senderUserId = record.userId();

        if (channelService.leave(senderUserId)) {
            clientNotificationService.sendMessage(senderUserId, new LeaveResponseRecord(senderUserId));
        } else {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.LEAVE_REQUEST, "Leave failed."));
        }
    }
}
