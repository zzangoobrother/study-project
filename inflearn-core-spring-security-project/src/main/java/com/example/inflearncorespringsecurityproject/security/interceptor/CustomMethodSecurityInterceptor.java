package com.example.inflearncorespringsecurityproject.security.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.SecurityMetadataSource;
import org.springframework.security.access.intercept.AbstractSecurityInterceptor;
import org.springframework.security.access.intercept.InterceptorStatusToken;
import org.springframework.security.access.method.MethodSecurityMetadataSource;

public class CustomMethodSecurityInterceptor extends AbstractSecurityInterceptor implements MethodInterceptor {
    private MethodSecurityMetadataSource securityMetadataSource;

    public Object invoke(MethodInvocation mi) throws Throwable{
        InterceptorStatusToken token = super.beforeInvocation(mi);

        Object result;
        try {
            result = mi.proceed();
        } finally {
            super.finallyInvocation(token);
        }

        return super.afterInvocation(token, result);
    }

    @Override
    public Class<?> getSecureObjectClass() {
        return MethodInvocation.class;
    }

    @Override
    public SecurityMetadataSource obtainSecurityMetadataSource() {
        return this.securityMetadataSource;
    }

    public void setSecurityMetadataSource(MethodSecurityMetadataSource securityMetadataSource) {
        this.securityMetadataSource = securityMetadataSource;
    }
}
