package com.example.global.Constants;

import lombok.Getter;

@Getter
public enum Constants {
	HTTP_SESSION_ID("HTTP_SESSION_ID");

	private final String value;

	Constants(String value) {
		this.value = value;
	}
}
