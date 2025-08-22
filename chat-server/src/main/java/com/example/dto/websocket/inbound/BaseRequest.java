package com.example.dto.websocket.inbound;

import com.example.global.Constants.MessageType;

public interface BaseRequest {

	MessageType getType();
}
