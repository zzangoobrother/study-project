package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;

public class JoinRequest extends BaseRequest {

    private final InviteCode inviteCode;

    public JoinRequest(InviteCode inviteCode) {
        super(MessageType.JOIN_REQUEST);
        this.inviteCode = inviteCode;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }
}
