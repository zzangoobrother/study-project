package com.example.domain.marketing;

import com.example.enums.AdvertiseType;
import com.example.enums.MarketingPlacement;
import com.example.enums.MarketingType;
import lombok.Getter;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "marketings", schema = "ecommerce")
public class Marketing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long marketingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketing_type")
    private MarketingType marketingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketing_placement")
    private MarketingPlacement marketingPlacement;

    @Enumerated(EnumType.STRING)
    @Column(name = "advertise_type")
    private AdvertiseType advertiseType;

    @Column(name = "advertise_value")
    private String advertiseValue;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
