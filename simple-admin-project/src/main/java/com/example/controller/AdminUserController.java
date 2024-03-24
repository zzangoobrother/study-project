package com.example.controller;

import com.example.domain.entity.AdminUser;
import com.example.service.AdminUserDetailService;
import com.example.service.dto.AdminUserFormDTO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AdminUserController {

    private final AdminUserDetailService adminUserDetailService;

    public AdminUserController(AdminUserDetailService adminUserDetailService) {
        this.adminUserDetailService = adminUserDetailService;
    }

    @GetMapping("/users/register")
    public String adminUserForm() {
        return "/users/register";
    }

    @PostMapping("/users/register")
    public String create(AdminUserFormDTO adminUserFormDTO) {
        AdminUser newAdminUser = AdminUser.createAdminUser(adminUserFormDTO);
        adminUserDetailService.save(newAdminUser);
        return "redirect:/";
    }
}
