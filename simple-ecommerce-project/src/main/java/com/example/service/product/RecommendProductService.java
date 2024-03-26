package com.example.service.product;

import com.example.controller.dto.home.RecommendProductDTO;
import com.example.domain.product.Product;
import com.example.repository.product.RecommendProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Transactional(readOnly = true)
@Service
public class RecommendProductService {

    private final RecommendProductRepository recommendProductRepository;

    public RecommendProductService(RecommendProductRepository recommendProductRepository) {
        this.recommendProductRepository = recommendProductRepository;
    }

    public List<RecommendProductDTO> recommend() {
        List<Product> recommendProducts = recommendProductRepository.findAllByJPQL();
        List<RecommendProductDTO> recommendProductDTOS = recommendProducts.stream().map((recommendProduct -> {
            RecommendProductDTO recommendProductDTO = new RecommendProductDTO(
                    recommendProduct.getId(),
                    recommendProduct.getProductName(),
                    recommendProduct.getPrice(),
                    recommendProduct.getImageUrl()
            );
            return recommendProductDTO;
        })).collect(Collectors.toList());
        log.debug("추천 상품 목록 : {}", recommendProductDTOS);
        return recommendProductDTOS;
    }
}
