package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.DisconnectRequestRecord;
import com.example.dto.kafka.DisconnectResponseRecord;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class DisconnectRequestHandler implements BaseRecordHandler<DisconnectRequestRecord> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public DisconnectRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(DisconnectRequestRecord record) {
        UserId senderUserId = record.userId();
        Pair<Boolean, String> result = userConnectionService.disconnect(senderUserId, record.username());
        if (result.getFirst()) {
            clientNotificationService.sendMessage(senderUserId, new DisconnectResponseRecord(senderUserId, record.username(), UserConnectionStatus.DISCONNECTED));
        } else {
            String errorMessage = result.getSecond();
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.DISCONNECT_REQUEST, errorMessage));
        }
    }
}
