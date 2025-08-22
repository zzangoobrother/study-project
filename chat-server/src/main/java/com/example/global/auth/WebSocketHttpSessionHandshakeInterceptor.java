package com.example.global.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import com.example.global.Constants.Constants;

import jakarta.servlet.http.HttpSession;

@Component
public class WebSocketHttpSessionHandshakeInterceptor extends HttpSessionHandshakeInterceptor {

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
		Map<String, Object> attributes) {
		if (request instanceof ServletServerHttpRequest servletServerHttpRequest) {
			HttpSession session = servletServerHttpRequest.getServletRequest().getSession(false);
			if (session != null) {
				attributes.put(Constants.HTTP_SESSION_ID.getValue(), session.getId());
				return true;
			} else {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}
		} else {
			response.setStatusCode(HttpStatus.BAD_REQUEST);
			return false;
		}
	}
}
