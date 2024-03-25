package com.example.domain.customer;

import com.example.enums.CustomerPermission;
import com.example.enums.ECommerceRole;
import lombok.Getter;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "customers", schema = "ecommerce")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long customerId;
    @Column(name = "name")
    private String name;
    @Column(name = "phone_number")
    private String phoneNumber;
    @Column(name = "email")
    private String email;
    @Column(name = "age")
    private int age;
    @Column(name = "password")
    private String password;
    @Column(name = "address")
    private String address;
    @Column(name = "role")
    @Enumerated(value = EnumType.STRING)
    private ECommerceRole role;
    @Column(name = "permission")
    @Enumerated(value = EnumType.STRING)
    private CustomerPermission permission;
    @Column(name = "is_deleted")
    private boolean isDeleted = false;
    @Column(name = "is_activated")
    private boolean isActivated = true;
    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
