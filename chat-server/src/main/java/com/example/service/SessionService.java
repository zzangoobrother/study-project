package com.example.service;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class SessionService {

	private final SessionRepository<? extends Session> sessionRepository;

	public void refreshTTL(String sessionId) {
		Session session = sessionRepository.findById(sessionId);
		if (session != null) {
			session.setLastAccessedTime(Instant.now());
		}
	}

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
