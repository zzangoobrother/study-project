package com.example.inflearncorespringsecurityproject.service;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.Map;

public interface SecurityResourceService {

    Map<RequestMatcher, List<ConfigAttribute>> getResourceList();

    Map<String, List<ConfigAttribute>> getMethodResourceList();

    List<String> getAccessIpList();

    Map<String, List<ConfigAttribute>> getPointcutResourceList();
}
