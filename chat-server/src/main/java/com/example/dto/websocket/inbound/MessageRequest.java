package com.example.dto.websocket.inbound;

import com.example.global.Constants.MessageType;

public record MessageRequest(
        String username,
        String content
) implements BaseRequest {

	@Override
	public MessageType getType() {
		return MessageType.MESSAGE;
	}
}
