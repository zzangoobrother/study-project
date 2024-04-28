package com.example.inflearncorespringsecurityproject.controller.admin;

import com.example.inflearncorespringsecurityproject.domain.dto.ResourcesDto;
import com.example.inflearncorespringsecurityproject.domain.entity.Resources;
import com.example.inflearncorespringsecurityproject.domain.entity.Role;
import com.example.inflearncorespringsecurityproject.repository.RoleRepository;
import com.example.inflearncorespringsecurityproject.service.ResourcesService;
import com.example.inflearncorespringsecurityproject.service.RoleService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class ResourcesController {

    private final ResourcesService resourcesService;
    private final RoleRepository roleRepository;
    private final RoleService roleService;

    public ResourcesController(ResourcesService resourcesService, RoleRepository roleRepository, RoleService roleService) {
        this.resourcesService = resourcesService;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
    }

    @GetMapping("/admin/resources")
    public String getResources(Model model) {
        List<Resources> resources = resourcesService.getResources();
        model.addAttribute("resources", resources);

        return "admin/resource/list";
    }

    @PostMapping("/admin/resources")
    public String createResources(ResourcesDto resourcesDto) {
        ModelMapper modelMapper = new ModelMapper();
        Role role = roleRepository.findByRoleName(resourcesDto.getRoleName());
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        Resources resources = modelMapper.map(resourcesDto, Resources.class);
        resources.setRoleSet(roles);

        resourcesService.createResources(resources);

        return "redirect:/admin/resources";
    }

    @GetMapping("/admin/resources/register")
    public String viewRoles(Model model) {
        List<Role> roles = roleService.getRoles();
        model.addAttribute("roleList", roles);

        ResourcesDto resources = new ResourcesDto();
        Set<Role> roleSet = new HashSet<>();
        roleSet.add(new Role());
        resources.setRoleSet(roleSet);
        model.addAttribute("resources", resources);

        return "admin/resource/detail";
    }

    @GetMapping("/admin/resources/{id}")
    public String getResources(@PathVariable Long id, Model model) {
        List<Role> roles = roleService.getRoles();
        model.addAttribute("roleList", roles);
        Resources resources = resourcesService.getResources(id);

        ModelMapper modelMapper = new ModelMapper();
        ResourcesDto resourcesDto = modelMapper.map(resources, ResourcesDto.class);
        model.addAttribute("resources", resourcesDto);

        return "admin/resource/detail";
    }

    @GetMapping("/admin/resources/delete/{id}")
    public String removeResources(@PathVariable Long id) {
        Resources resources = resourcesService.getResources(id);
        resourcesService.deleteResources(id);

        return "redirect:/admin/resources";
    }
}
