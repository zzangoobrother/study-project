package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.InviteCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JoinRequest extends BaseRequest {

    private final InviteCode inviteCode;

    @JsonCreator
    public JoinRequest(@JsonProperty("inviteCode") InviteCode inviteCode) {
        super(MessageType.JOIN_REQUEST);
        this.inviteCode = inviteCode;
    }

    public InviteCode getInviteCode() {
        return inviteCode;
    }
}
