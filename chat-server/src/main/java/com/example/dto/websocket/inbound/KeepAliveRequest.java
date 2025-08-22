package com.example.dto.websocket.inbound;

import com.example.global.Constants.MessageType;

public record KeepAliveRequest(

) implements BaseRequest {
	@Override
	public MessageType getType() {
		return MessageType.KEEP_ALIVE;
	}
}
