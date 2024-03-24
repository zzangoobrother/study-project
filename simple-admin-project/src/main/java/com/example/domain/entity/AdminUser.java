package com.example.domain.entity;

import com.example.enums.AdminUserPermission;
import com.example.enums.AdminUserRole;
import lombok.Getter;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Getter
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
}
