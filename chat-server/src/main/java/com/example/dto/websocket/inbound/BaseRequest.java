package com.example.dto.websocket.inbound;

import com.example.global.constants.MessageType;

public interface BaseRequest {

	MessageType getType();
}
