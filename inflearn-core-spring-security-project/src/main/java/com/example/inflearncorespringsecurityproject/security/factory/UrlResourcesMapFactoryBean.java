package com.example.inflearncorespringsecurityproject.security.factory;

import com.example.inflearncorespringsecurityproject.service.SecurityResourceService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UrlResourcesMapFactoryBean implements FactoryBean<Map<RequestMatcher, List<ConfigAttribute>>> {

    private Map<RequestMatcher, List<ConfigAttribute>> resourceMap;

    private final SecurityResourceService securityResourceService;

    public UrlResourcesMapFactoryBean(SecurityResourceService securityResourceService) {
        this.securityResourceService = securityResourceService;
    }

    @Override
    public Map<RequestMatcher, List<ConfigAttribute>> getObject() throws Exception {
        if (resourceMap == null) {
            init();
        }

        return resourceMap;
    }

    private void init() {
        resourceMap = securityResourceService.getResourceList();
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
