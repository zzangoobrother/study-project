package com.example.authorization;

public class AuthorizationContextHolder {

    private static final ThreadLocal<AuthorizationContext> contextHolder = new ThreadLocal<>();

    public static void setContext(AuthorizationContext context) {
        contextHolder.set(context);
    }

    public static AuthorizationContext getContextHolder() {
        return contextHolder.get();
    }

    public static boolean isAuthorized() {
        return contextHolder.get() != null;
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}
