package com.example.domain;

import com.example.domain.entity.AdminUser;
import com.example.enums.AdminUserPermission;
import com.example.enums.AdminUserRole;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdminUserDetail {
    private AdminUser adminUser;
    private List<AdminUserRole> roles;
    private List<AdminUserPermission> permissions;

//    @Override
//    public Collection<? extends GrantedAuthority> getAuthorities() {
//        List<GrantedAuthority> authorities = new ArrayList<>();
//
//        GrantedAuthority permissionAuthority = new SimpleGrantedAuthority(adminUser.getPermission().name());
//        authorities.add(permissionAuthority);
//
//        GrantedAuthority roleAuthority = new SimpleGrantedAuthority("ROLE_" + adminUser.getRole().name());
//        authorities.add(roleAuthority);
//
//        return authorities;
//    }
//
//    @Override
//    public String getPassword() {
//        return adminUser.getPassword();
//    }
//
//    @Override
//    public String getUsername() {
//        return adminUser.getUsername();
//    }
//
//    @Override
//    public boolean isAccountNonExpired() {
//        return adminUser.isActivated();
//    }
//
//    @Override
//    public boolean isAccountNonLocked() {
//        return adminUser.isActivated();
//    }
//
//    @Override
//    public boolean isCredentialsNonExpired() {
//        return adminUser.isActivated();
//    }
//
//    @Override
//    public boolean isEnabled() {
//        return adminUser.isActivated();
//    }
//
//    public Iterable<String> getPermissionList() {
//        return roles.stream().map(r -> r.name()).collect(Collectors.toList());
//    }
//
//    public Iterable<String> getRoleList() {
//        return permissions.stream().map(p -> p.name()).collect(Collectors.toList());
//    }
}
