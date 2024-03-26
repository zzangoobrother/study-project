package com.example.repository.marketing;

import com.example.domain.marketing.Marketing;
import com.example.enums.AdvertiseType;
import com.example.enums.MarketingPlacement;
import com.example.enums.MarketingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketingRepository extends JpaRepository<Marketing, Long> {

    Optional<Marketing> findFirstByMarketingTypeAndMarketingPlacementAndAdvertiseType(MarketingType marketingType, MarketingPlacement marketingPlacement, AdvertiseType advertiseType);
}
