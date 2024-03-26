package com.example.domain.product;

import lombok.Getter;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Getter
@Table(name = "recommend_products", schema = "ecommerce")
@Entity
public class RecommendProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long recommendProductId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "is_deleted")
    private boolean isDeleted;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
