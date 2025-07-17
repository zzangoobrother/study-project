package com.example.handler.kafka;

import com.example.dto.domain.Connection;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.FetchConnectionsRequestRecord;
import com.example.dto.kafka.FetchConnectionsResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchConnectionsRequestHandler implements BaseRecordHandler<FetchConnectionsRequestRecord> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public FetchConnectionsRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchConnectionsRequestRecord record) {
        UserId senderUserId = record.userId();
        List<Connection> connections = userConnectionService.getUsersByStatus(senderUserId, record.status()).stream()
                .map(user -> new Connection(user.username(), record.status()))
                .toList();

        clientNotificationService.sendMessage(senderUserId, new FetchConnectionsResponseRecord(senderUserId, connections));
    }
}
