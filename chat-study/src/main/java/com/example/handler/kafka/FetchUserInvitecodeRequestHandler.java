package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.FetchUserInvitecodeRequestRecord;
import com.example.dto.kafka.FetchUserInvitecodeResponseRecord;
import com.example.service.ClientNotificationService;
import com.example.service.UserService;
import org.springframework.stereotype.Component;

@Component
public class FetchUserInvitecodeRequestHandler implements BaseRecordHandler<FetchUserInvitecodeRequestRecord> {

    private final UserService userService;
    private final ClientNotificationService clientNotificationService;

    public FetchUserInvitecodeRequestHandler(UserService userService, ClientNotificationService clientNotificationService) {
        this.userService = userService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchUserInvitecodeRequestRecord record) {
        UserId senderUserId = record.userId();
        userService.getInviteCode(senderUserId)
                .ifPresentOrElse(
                        inviteCode -> clientNotificationService.sendMessage(senderUserId, new FetchUserInvitecodeResponseRecord(senderUserId, inviteCode)),
                        () -> clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.FETCH_USER_INVITECODE_REQUEST, "Fetch user invite code failed."))
                );
    }
}
