package com.example.inflearncorespringsecurityproject.controller.admin;

import com.example.inflearncorespringsecurityproject.domain.dto.AccountDto;
import com.example.inflearncorespringsecurityproject.domain.entity.Account;
import com.example.inflearncorespringsecurityproject.domain.entity.Role;
import com.example.inflearncorespringsecurityproject.service.RoleService;
import com.example.inflearncorespringsecurityproject.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class UserManagerController {

    private final UserService userService;
    private final RoleService roleService;

    public UserManagerController(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @GetMapping("/admin/accounts")
    public String getUsers(Model model) {
        List<Account> accounts = userService.getUsers();
        model.addAttribute("accounts", accounts);

        return "admin/user/list";
    }

    @PostMapping("/admin/accounts")
    public String modifyUser(AccountDto accountDto) {
        userService.modifyUser(accountDto);

        return "redirect:/admin/accounts";
    }

    @GetMapping("/admin/accounts/{id}")
    public String getUser(@PathVariable Long id, Model model) {
        AccountDto accountDto = userService.getUser(id);
        List<Role> roles = roleService.getRoles();

        model.addAttribute("account", accountDto);
        model.addAttribute("roleList", roles);

        return "admin/user/detail";
    }

    @GetMapping("/admin/accounts/delete/{id}")
    public String removeUser(@PathVariable Long id) {
        userService.deleteUser(id);

        return "redirect:/admin/users";
    }
}
