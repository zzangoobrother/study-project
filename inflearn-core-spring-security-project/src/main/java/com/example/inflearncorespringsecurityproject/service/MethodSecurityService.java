package com.example.inflearncorespringsecurityproject.service;

public interface MethodSecurityService {

    void addMethodSecured(String className, String roleName) throws Exception;

    void removeMethodSecured(String className) throws Exception;
}
