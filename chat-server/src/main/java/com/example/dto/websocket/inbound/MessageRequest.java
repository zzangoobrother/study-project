package com.example.dto.websocket.inbound;

import com.example.global.constants.MessageType;

public record MessageRequest(
        String username,
        String content
) implements BaseRequest {

	@Override
	public MessageType getType() {
		return MessageType.MESSAGE;
	}
}
