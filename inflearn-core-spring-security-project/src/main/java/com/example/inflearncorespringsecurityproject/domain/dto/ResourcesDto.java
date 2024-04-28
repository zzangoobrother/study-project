package com.example.inflearncorespringsecurityproject.domain.dto;

import com.example.inflearncorespringsecurityproject.domain.entity.Role;
import lombok.*;

import java.util.Set;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcesDto {

    private String id;
    private String resourceName;
    private String httpMethod;
    private int orderNum;
    private String resourceType;
    private String roleName;
    private Set<Role> roleSet;

}
