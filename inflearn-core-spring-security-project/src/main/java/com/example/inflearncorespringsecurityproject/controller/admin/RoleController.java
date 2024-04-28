package com.example.inflearncorespringsecurityproject.controller.admin;

import com.example.inflearncorespringsecurityproject.domain.dto.RoleDto;
import com.example.inflearncorespringsecurityproject.domain.entity.Role;
import com.example.inflearncorespringsecurityproject.service.RoleService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/admin/roles")
    public String getRoles(Model model) {
        List<Role> roles = roleService.getRoles();
        model.addAttribute("roles", roles);

        return "admin/role/list";
    }

    @GetMapping("/admin/roles/register")
    public String viewRoles(Model model) {
        RoleDto roleDto = new RoleDto();
        model.addAttribute("role", roleDto);

        return "admin/role/detail";
    }

    @PostMapping("/admin/roles")
    public String createRole(RoleDto roleDto) {
        ModelMapper modelMapper = new ModelMapper();
        Role role = modelMapper.map(roleDto, Role.class);
        roleService.createRole(role);

        return "redirect:/admin/roles";
    }

    @GetMapping("/admin/roles/{id}")
    public String getRole(@PathVariable Long id, Model model) {
        Role role = roleService.getRole(id);

        ModelMapper modelMapper = new ModelMapper();
        RoleDto roleDto = modelMapper.map(role, RoleDto.class);
        model.addAttribute("role", roleDto);

        return "admin/role/detail";
    }

    @GetMapping("/admin/roles/delete/{id}")
    public String removeResources(@PathVariable Long id, Model model) {
        Role role = roleService.getRole(id);
        roleService.deleteRole(id);

        return "redirect:/admin/resources";
    }
}
