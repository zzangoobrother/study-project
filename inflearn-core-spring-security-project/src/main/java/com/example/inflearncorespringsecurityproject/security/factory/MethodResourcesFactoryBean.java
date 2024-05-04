package com.example.inflearncorespringsecurityproject.security.factory;

import com.example.inflearncorespringsecurityproject.service.SecurityResourceService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.security.access.ConfigAttribute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MethodResourcesFactoryBean implements FactoryBean<Map<String, List<ConfigAttribute>>> {
    private Map<String, List<ConfigAttribute>> resourceMap;

    private final SecurityResourceService securityResourceService;

    public MethodResourcesFactoryBean(SecurityResourceService securityResourceService) {
        this.securityResourceService = securityResourceService;
    }

    @Override
    public Map<String, List<ConfigAttribute>> getObject() {
        if (resourceMap == null) {
            init();
        }

        return resourceMap;
    }

    private void init() {
        resourceMap = securityResourceService.getMethodResourceList();
    }

    @Override
    public Class<?> getObjectType() {
        return LinkedHashMap.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
