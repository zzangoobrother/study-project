package com.example.inflearncorespringsecurityproject.service.impl;

import com.example.inflearncorespringsecurityproject.domain.entity.RoleHierarchy;
import com.example.inflearncorespringsecurityproject.repository.RoleHierarchyRepository;
import com.example.inflearncorespringsecurityproject.service.RoleHierarchyService;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;

@Service
public class RoleHierarchyServiceImpl implements RoleHierarchyService {

    private final RoleHierarchyRepository roleHierarchyRepository;

    public RoleHierarchyServiceImpl(RoleHierarchyRepository roleHierarchyRepository) {
        this.roleHierarchyRepository = roleHierarchyRepository;
    }

    @Override
    public String findAllHierarchy() {
        List<RoleHierarchy> roleHierarchies = roleHierarchyRepository.findAll();

        Iterator<RoleHierarchy> iterator = roleHierarchies.iterator();
        StringBuilder concatedRoles = new StringBuilder();
        while (iterator.hasNext()) {
            RoleHierarchy roleHierarchy = iterator.next();
            if (roleHierarchy.getParentName() != null) {
                concatedRoles.append(roleHierarchy.getParentName().getChildName());
                concatedRoles.append(" > ");
                concatedRoles.append(roleHierarchy.getChildName());
                concatedRoles.append("\n");
            }
        }

        return concatedRoles.toString();
    }
}
