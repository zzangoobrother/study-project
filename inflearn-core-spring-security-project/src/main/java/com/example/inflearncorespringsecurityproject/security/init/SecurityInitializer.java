package com.example.inflearncorespringsecurityproject.security.init;

import com.example.inflearncorespringsecurityproject.service.RoleHierarchyService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.stereotype.Component;

@Component
public class SecurityInitializer implements ApplicationRunner {

    private final RoleHierarchyService roleHierarchyService;
    private final RoleHierarchyImpl roleHierarchy;

    public SecurityInitializer(RoleHierarchyService roleHierarchyService, RoleHierarchyImpl roleHierarchy) {
        this.roleHierarchyService = roleHierarchyService;
        this.roleHierarchy = roleHierarchy;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String hierarchy = roleHierarchyService.findAllHierarchy();
        roleHierarchy.setHierarchy(hierarchy);
    }
}
