package com.example.inflearncorespringsecurityproject.service;

import com.example.inflearncorespringsecurityproject.domain.entity.Role;

import java.util.List;

public interface RoleService {

    Role getRole(Long id);

    List<Role> getRoles();

    void createRole(Role role);

    void deleteRole(long id);
}
