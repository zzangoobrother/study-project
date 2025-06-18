package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchUserInvitecodeResponse extends BaseMessage {

    private final InviteCode inviteCode;

    @JsonCreator
    public FetchUserInvitecodeResponse(@JsonProperty("inviteCode") InviteCode inviteCode) {
        super(MessageType.FETCH_USER_INVITECODE_RESPONSE);
        this.inviteCode = inviteCode;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }
}
