package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.AcceptNotificationRecord;
import com.example.dto.kafka.AcceptRequestRecord;
import com.example.dto.kafka.AcceptResponseRecord;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AcceptRequestRecordHandler implements BaseRecordHandler<AcceptRequestRecord> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public AcceptRequestRecordHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(AcceptRequestRecord record) {
        UserId acceptorUserId = record.userId();
        Pair<Optional<UserId>, String> result = userConnectionService.accept(acceptorUserId, record.username());
        result.getFirst().ifPresentOrElse(inviterUserId -> {
            clientNotificationService.sendMessage(acceptorUserId, new AcceptResponseRecord(acceptorUserId, record.username()));
            String acceptorUsername = result.getSecond();
            clientNotificationService.sendMessage(inviterUserId, new AcceptNotificationRecord(inviterUserId, acceptorUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            clientNotificationService.sendError(new ErrorResponseRecord(acceptorUserId, MessageType.ACCEPT_REQUEST, errorMessage));
        });
    }
}
