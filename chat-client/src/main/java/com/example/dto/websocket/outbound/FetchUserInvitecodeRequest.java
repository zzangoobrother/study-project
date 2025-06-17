package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;

public class FetchUserInvitecodeRequest extends BaseRequest {

    @JsonCreator
    public FetchUserInvitecodeRequest() {
        super(MessageType.FETCH_USER_INVITECODE_REQUEST);
    }
}
