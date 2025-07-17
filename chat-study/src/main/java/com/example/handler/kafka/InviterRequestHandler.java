package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.InviteNotificationRecord;
import com.example.dto.kafka.InviteRequestRecord;
import com.example.dto.kafka.InviteResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InviterRequestHandler implements BaseRecordHandler<InviteRequestRecord> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public InviterRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(InviteRequestRecord record) {
        UserId inviterUserId = record.userId();
        Pair<Optional<UserId>, String> result = userConnectionService.invite(inviterUserId,
                record.userInviteCode());
        result.getFirst().ifPresentOrElse(partnerUserId -> {
            String inviterUsername = result.getSecond();
            clientNotificationService.sendMessage(inviterUserId, new InviteResponseRecord(inviterUserId, record.userInviteCode(), UserConnectionStatus.PENDING));
            clientNotificationService.sendMessage(partnerUserId, new InviteNotificationRecord(partnerUserId, inviterUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            clientNotificationService.sendError(new ErrorResponseRecord(inviterUserId, MessageType.INVITE_REQUEST, errorMessage));
        });
    }
}
