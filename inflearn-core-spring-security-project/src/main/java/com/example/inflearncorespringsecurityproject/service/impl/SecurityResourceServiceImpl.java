package com.example.inflearncorespringsecurityproject.service.impl;

import com.example.inflearncorespringsecurityproject.domain.entity.AccessIp;
import com.example.inflearncorespringsecurityproject.domain.entity.Resources;
import com.example.inflearncorespringsecurityproject.repository.AccessIpRepository;
import com.example.inflearncorespringsecurityproject.repository.ResourcesRepository;
import com.example.inflearncorespringsecurityproject.service.SecurityResourceService;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SecurityResourceServiceImpl implements SecurityResourceService {

    private final ResourcesRepository resourcesRepository;
    private final AccessIpRepository accessIpRepository;

    public SecurityResourceServiceImpl(ResourcesRepository resourcesRepository, AccessIpRepository accessIpRepository) {
        this.resourcesRepository = resourcesRepository;
        this.accessIpRepository = accessIpRepository;
    }


    @Override
    public Map<RequestMatcher, List<ConfigAttribute>> getResourceList() {
        Map<RequestMatcher, List<ConfigAttribute>> result = new LinkedHashMap<>();
        List<Resources> resources = resourcesRepository.findAllResources();
        resources.forEach(re -> {
            List<ConfigAttribute> configAttributes = new ArrayList<>();
            re.getRoleSet().forEach(role -> configAttributes.add(new SecurityConfig(role.getRoleName())));

            result.put(new AntPathRequestMatcher(re.getResourceName()), configAttributes);
        });

        return result;
    }

    @Override
    public Map<String, List<ConfigAttribute>> getMethodResourceList() {
        Map<String, List<ConfigAttribute>> result = new LinkedHashMap<>();
        List<Resources> resources = resourcesRepository.findAllMethodResources();
        resources.forEach(re -> {
            List<ConfigAttribute> configAttributes = new ArrayList<>();
            re.getRoleSet().forEach(role -> configAttributes.add(new SecurityConfig(role.getRoleName())));

            result.put(re.getResourceName(), configAttributes);
        });

        return result;
    }

    @Override
    public List<String> getAccessIpList() {
        return accessIpRepository.findAll().stream()
                .map(AccessIp::getIpAddress)
                .toList();
    }
}
