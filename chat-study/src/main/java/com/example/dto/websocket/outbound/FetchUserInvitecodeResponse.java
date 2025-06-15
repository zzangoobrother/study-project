package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;

public class FetchUserInvitecodeResponse extends BaseMessage {

    private final InviteCode inviteCode;

    public FetchUserInvitecodeResponse(InviteCode inviteCode) {
        super(MessageType.FETCH_USER_INVITECODE_RESPONSE);
        this.inviteCode = inviteCode;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }
}
