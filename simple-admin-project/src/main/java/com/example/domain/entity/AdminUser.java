package com.example.domain.entity;

import com.example.enums.AdminUserPermission;
import com.example.enums.AdminUserRole;
import com.example.service.dto.AdminUserFormDTO;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "users", schema = "ecommerce")
public class AdminUser {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "role")
    @Enumerated(value = EnumType.STRING)
    private AdminUserRole role;

    @Column(name = "permission")
    @Enumerated(value = EnumType.STRING)
    private AdminUserPermission permission;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "is_activated")
    private boolean isActivated = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public static AdminUser createAdminUser(AdminUserFormDTO adminUserFormDTO) {
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(adminUserFormDTO.username());
        adminUser.setPassword(adminUserFormDTO.password1());
        adminUser.setEmail(adminUserFormDTO.email());
        adminUser.setRole(AdminUserRole.USER);
        adminUser.setPermission(AdminUserPermission.ALL);

        OffsetDateTime now = OffsetDateTime.now();
        adminUser.setCreatedAt(now);
        adminUser.setUpdatedAt(now);
        adminUser.setActivated(true);

        return adminUser;
    }
}
