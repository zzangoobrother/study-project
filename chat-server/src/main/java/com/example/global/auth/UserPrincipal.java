package com.example.global.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {
	private final Long userId;
	private final String username;
	private String password;

	public UserPrincipal(Long userId, String username, String password) {
		this.userId = userId;
		this.username = username;
		this.password = password;
	}

	public void erasePassword() {
		this.password = "";
	}

	public Long getUserId() {
		return userId;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return this.password;
	}

	@Override
	public String getUsername() {
		return this.username;
	}
}
