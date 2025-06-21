package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;

public class CreateRequest extends BaseRequest {

    private final String title;
    private final String participantUsername;

    public CreateRequest(String title, String participantUsername) {
        super(MessageType.CREATE_REQUEST);
        this.title = title;
        this.participantUsername = participantUsername;
    }

    public String getTitle() {
        return title;
    }

    public String getParticipantUsername() {
        return participantUsername;
    }
}
