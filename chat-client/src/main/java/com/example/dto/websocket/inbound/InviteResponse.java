package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.InviteCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InviteResponse extends BaseMessage {

    private final InviteCode inviteCode;
    private final UserConnectionStatus status;

    @JsonCreator
    public InviteResponse(@JsonProperty("inviteCode") InviteCode inviteCode, @JsonProperty("status") UserConnectionStatus status) {
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
