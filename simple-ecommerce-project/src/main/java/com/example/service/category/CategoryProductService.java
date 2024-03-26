package com.example.service.category;

import com.example.controller.dto.product.CategoryProductDTO;
import com.example.repository.category.CategoryProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Transactional
@Service
public class CategoryProductService {

    private final CategoryProductRepository categoryProductRepository;

    public CategoryProductService(CategoryProductRepository categoryProductRepository) {
        this.categoryProductRepository = categoryProductRepository;
    }

    public List<CategoryProductDTO> findAllBy(Long categoryId, Pageable pageable) {
        return this.categoryProductRepository.findAllByCategoryIdAndIsDeletedNot(categoryId, pageable);
    }
}
