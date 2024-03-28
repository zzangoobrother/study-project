package com.example.repository.product;

import com.example.controller.dto.search.SearchResultDTO;
import com.example.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(
            value = "SELECT new com.example.controller.dto.search.SearchResultDTO(p.id, p.productName, p.imageUrl, p.price) " +
                    "FROM Product p " +
                    "WHERE (p.productName LIKE %:keyword% OR p.productDesc LIKE %:keyword%) AND p.isDeleted = false AND p.isExposed = true " +
                    "ORDER BY p.updatedAt DESC "
    )
    List<SearchResultDTO> search(@Param(value = "keyword") String keyword);
}
