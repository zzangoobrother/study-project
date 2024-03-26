package com.example.repository.category;

import com.example.controller.dto.product.CategoryProductDTO;
import com.example.domain.product.CategoryProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryProductRepository extends JpaRepository<CategoryProduct, Long> {

    @Query(
            value = "SELECT new com.example.controller.dto.product.CategoryProductDTO(cp.categoryProductId, cp.categoryId, cp.productId, p.productName, p.imageUrl, p.price) " +
                    "FROM CategoryProduct cp " +
                    "INNER JOIN Product p ON cp.productId = p.id " +
                    "WHERE cp.isDeleted = false AND cp.isExposed = true " +
                    "ORDER BY cp.updatedAt DESC "
    )
    List<CategoryProductDTO> findAllByCategoryIdAndIsDeletedNot(Long categoryId, Pageable pageable);
}
