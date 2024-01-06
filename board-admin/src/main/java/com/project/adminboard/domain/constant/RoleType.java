package com.project.adminboard.domain.constant;

import lombok.Getter;

public enum RoleType {
    USER("ROLE_USER", "사용자"),
    MANAGER("ROLE_MANAGER", "운영자"),
    DEVELOPER("ROLE_DEVELOPER", "개발자"),
    ADMIN("ROLE_ADMIN", "관리자");

    @Getter
    private final String roleName;

    @Getter
    private final String description;

    RoleType(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }
}
