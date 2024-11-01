package com.example.service.impl;

import com.example.dto.CategoryDto;
import com.example.mapper.CategoryMapper;
import com.example.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public void register(String accountId, CategoryDto categoryDto) {
        if (accountId != null) {
            categoryMapper.register(categoryDto);
        } else {
            log.error("register ERROR : {}", categoryDto);
            throw new RuntimeException("register Error 게시글 카테고리 등록 메서드를 확인해주세요." + categoryDto);
        }
    }

    @Override
    public void update(CategoryDto categoryDto) {
        if (categoryDto != null) {
            categoryMapper.updateCategory(categoryDto);
        } else {
            log.error("update ERROR : {}", categoryDto);
            throw new RuntimeException("update Error 게시글 카테고리 수정 메서드를 확인해주세요." + categoryDto);
        }
    }

    @Override
    public void delete(int categoryId) {
        if (categoryId != 0) {
            categoryMapper.deleteCategory(categoryId);
        } else {
            log.error("delete ERROR : {}", categoryId);
            throw new RuntimeException("delete Error 게시글 카테고리 삭제 메서드를 확인해주세요." + categoryId);
        }
    }
}
