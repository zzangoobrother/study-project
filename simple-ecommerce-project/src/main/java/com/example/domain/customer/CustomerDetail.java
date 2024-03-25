package com.example.domain.customer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class CustomerDetail implements UserDetails {

    private Customer customer;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 퍼미션 목록
        GrantedAuthority permissionAuthority = new SimpleGrantedAuthority(this.customer.getPermission().name());
        authorities.add(permissionAuthority);

        // 역할 목록
        GrantedAuthority roleAuthority = new SimpleGrantedAuthority("ROLE_" + this.customer.getRole().name());
        authorities.add(roleAuthority);

        return authorities;
    }

    @Override
    public String getPassword() {
        return customer.getPassword();
    }

    @Override
    public String getUsername() {
        return customer.getName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return customer.isActivated();
    }

    @Override
    public boolean isAccountNonLocked() {
        return customer.isActivated();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return customer.isActivated();
    }

    @Override
    public boolean isEnabled() {
        return customer.isActivated();
    }
}
