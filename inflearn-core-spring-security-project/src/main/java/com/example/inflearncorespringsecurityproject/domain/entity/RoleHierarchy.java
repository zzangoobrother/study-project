package com.example.inflearncorespringsecurityproject.domain.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class RoleHierarchy implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "child_name")
    private String childName;

    @ManyToOne(cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_name", referencedColumnName = "child_name")
    private RoleHierarchy parentName;

    @OneToMany(mappedBy = "parentName", cascade = {CascadeType.ALL})
    private Set<RoleHierarchy> roleHierarchies = new HashSet<>();

    @Builder
    public RoleHierarchy(Long id, String childName, RoleHierarchy parentName, Set<RoleHierarchy> roleHierarchies) {
        this.id = id;
        this.childName = childName;
        this.parentName = parentName;
        this.roleHierarchies = roleHierarchies;
    }
}
