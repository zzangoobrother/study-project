package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.InviteCode;

public class InviteResponse extends BaseMessage {

    private final InviteCode inviteCode;
    private final UserConnectionStatus status;

    public InviteResponse(InviteCode inviteCode, UserConnectionStatus status) {
        super(MessageType.INVITE_RESPONSE);
        this.inviteCode = inviteCode;
        this.status = status;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }

    public UserConnectionStatus getStatus() {
        return status;
    }
}
