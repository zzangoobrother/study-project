package com.example.controller;

import com.example.aop.LoginCheck;
import com.example.dto.CategoryDto;
import com.example.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/categories")
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @LoginCheck(type = LoginCheck.UserType.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public void registerCategory(String accountId, @RequestBody CategoryDto categoryDto) {
        categoryService.register(accountId, categoryDto);
    }

    @LoginCheck(type = LoginCheck.UserType.ADMIN)
    @PatchMapping("/{categoryId}")
    public void updateCategory(String accountId, @PathVariable int categoryId, @RequestBody CategoryRequest request) {
        CategoryDto categoryDto = new CategoryDto(categoryId, request.name(), CategoryDto.SortStatus.NEWEST, 10, 1);
        categoryService.update(categoryDto);
    }

    @LoginCheck(type = LoginCheck.UserType.ADMIN)
    @DeleteMapping("/{categoryId}")
    public void deleteCategory(String accountId, @PathVariable int categoryId) {
        categoryService.delete(categoryId);
    }

    private static record CategoryRequest(
            int id,
            String name
    ) {

    }
}
