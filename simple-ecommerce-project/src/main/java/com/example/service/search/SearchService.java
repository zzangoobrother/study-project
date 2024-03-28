package com.example.service.search;

import com.example.controller.dto.search.SearchResultDTO;
import com.example.repository.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Transactional
@Service
public class SearchService {

    private final ProductRepository productRepository;

    public SearchService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<SearchResultDTO> search(String keyword) {
        List<SearchResultDTO> searchResultDTOS = this.productRepository.search(keyword);
        if (CollectionUtils.isEmpty(searchResultDTOS)) {
            return Collections.EMPTY_LIST;
        }
        return searchResultDTOS;
    }
}
