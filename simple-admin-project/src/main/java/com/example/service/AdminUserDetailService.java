package com.example.service;

import com.example.domain.entity.AdminUser;
import com.example.exception.DuplicatedAdminUserException;
import com.example.repository.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Service
public class AdminUserDetailService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserDetailService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        Optional<AdminUser> optionalAdminUser = adminUserRepository.findByEmailAndIsActivated(username, true);
//        if (optionalAdminUser.isEmpty()) {
//            throw new NotFoundAdminUserException("Not found admin user with " + username);
//        }
//
//        AdminUser adminUser = optionalAdminUser.get();
//        AdminUserDetail adminUserDetail = new AdminUserDetail();
//        adminUserDetail.setAdminUser(adminUser);
//        adminUserDetail.setRoles(Arrays.asList(adminUser.getRole()));
//        adminUserDetail.setPermissions(Arrays.asList(adminUser.getPermission()));
//        return adminUserDetail;
//    }

    public AdminUser save(AdminUser adminUser) {
        Optional<AdminUser> optionalAdminUser = adminUserRepository.findByEmailAndIsActivated(adminUser.getEmail(), true);
        if (optionalAdminUser.isPresent()) {
            throw new DuplicatedAdminUserException("Already register admin user, {}" + adminUser.getEmail());
        }

        return adminUserRepository.save(adminUser);
    }
}
