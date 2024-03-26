package com.example.service.category;

import com.example.domain.category.Category;
import com.example.repository.category.CategoryRepository;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public String getName(Long categoryId) {
        Optional<Category> optionalCategory = this.categoryRepository.findById(categoryId);
        if (optionalCategory.isEmpty()) {
            return Strings.EMPTY;
        }
        return optionalCategory.get().getCategoryName();
    }
}
