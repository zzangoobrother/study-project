package com.example.auth;

import com.example.constants.IdKey;
import com.example.dto.domain.UserId;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketHttpSessionHandshakeInterceptor extends HttpSessionHandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHttpSessionHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletServerHttpRequest) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                log.warn("WebSocket handshake failed, authentication is null");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            HttpSession httpSession = servletServerHttpRequest.getServletRequest().getSession(false);
            if (httpSession == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return true;
            }

            MessageUserDetails messageUserDetails = (MessageUserDetails) authentication.getPrincipal();
            attributes.put(IdKey.HTTP_SESSION_ID.getValue(), httpSession.getId());
            attributes.put(IdKey.USER_ID.getValue(), new UserId(messageUserDetails.getUserId()));
            return true;
        } else {
            log.info("WebSocket Handshake failed. request : {}", request.getClass());
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }
}
