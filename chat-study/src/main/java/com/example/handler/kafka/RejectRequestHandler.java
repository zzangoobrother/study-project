package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.RejectRequestRecord;
import com.example.dto.kafka.RejectResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class RejectRequestHandler implements BaseRecordHandler<RejectRequestRecord> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public RejectRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(RejectRequestRecord record) {
        UserId senderUserId = record.userId();
        Pair<Boolean, String> result = userConnectionService.reject(senderUserId, record.username());
        if (result.getFirst()) {
            clientNotificationService.sendMessage(senderUserId, new RejectResponseRecord(senderUserId, record.username(), UserConnectionStatus.REJECTED));
        } else {
            String errorMessage = result.getSecond();
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.REJECT_REQUEST, errorMessage));
        }
    }
}
