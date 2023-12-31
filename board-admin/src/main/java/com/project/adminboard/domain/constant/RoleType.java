package com.project.adminboard.domain.constant;

import lombok.Getter;

public enum RoleType {
    USER("ROLE_USER"),
    MANAGER("ROLE_MANAGER"),
    DEVELOPER("ROLE_DEVELOPER"),
    ADMIN("ROLE_ADMIN");

    @Getter
    private final String roleName;

    RoleType(String roleName) {
        this.roleName = roleName;
    }
}
