package com.example.authorization;

import com.example.http.Session;

import java.util.List;

public class AuthorizationContext {

    private Session session;

    private List<String> roles;

    public AuthorizationContext(Session session, List<String> roles) {
        this.session = session;
        this.roles = roles;
    }

    public Session getSession() {
        return session;
    }

    public List<String> getRoles() {
        return roles;
    }
}
