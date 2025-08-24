package com.example.global.constants;

import lombok.Getter;

@Getter
public enum Constants {
	HTTP_SESSION_ID("HTTP_SESSION_ID");

	private final String value;

	Constants(String value) {
		this.value = value;
	}
}
