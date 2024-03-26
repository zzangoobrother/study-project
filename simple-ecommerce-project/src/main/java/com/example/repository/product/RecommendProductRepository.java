package com.example.repository.product;

import com.example.domain.product.Product;
import com.example.domain.product.RecommendProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecommendProductRepository extends JpaRepository<RecommendProduct, Long> {

    @Query("SELECT p " +
            "FROM RecommendProduct rp " +
            "INNER JOIN Product p ON rp.productId = p.id " +
            "WHERE rp.isDeleted = false")
    List<Product> findAllByJPQL();
}
