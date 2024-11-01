package com.example.service;

import com.example.dto.CategoryDto;

public interface CategoryService {

    void register(String accountId, CategoryDto categoryDto);

    void update(CategoryDto categoryDto);

    void delete(int categoryId);
}
