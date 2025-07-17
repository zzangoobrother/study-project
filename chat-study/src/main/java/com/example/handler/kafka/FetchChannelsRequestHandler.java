package com.example.handler.kafka;

import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchChannelsRequestRecord;
import com.example.dto.kafka.FetchChannelsResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchChannelsRequestHandler implements BaseRecordHandler<FetchChannelsRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public FetchChannelsRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchChannelsRequestRecord record) {
        UserId senderUserId = record.userId();
        clientNotificationService.sendMessage(senderUserId, new FetchChannelsResponseRecord(senderUserId, channelService.getChannels(senderUserId)));
    }
}
