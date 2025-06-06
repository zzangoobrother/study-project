package com.example.auth;

import com.example.contants.Constants;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketHttpSessionHandshakeInterceptor extends HttpSessionHandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHttpSessionHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletServerHttpRequest) {
            HttpSession httpSession = servletServerHttpRequest.getServletRequest().getSession(false);
            if (httpSession != null) {
                attributes.put(Constants.HTTP_SESSION_ID.getValue(), httpSession.getId());
                return true;
            } else {
                log.info("WebSocket Handshake failed. httpSession is null");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        } else {
            log.info("WebSocket Handshake failed. request : {}", request.getClass());
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }
}
