package com.example.service;

import com.example.controller.dto.home.HomeDisplayDTO;
import com.example.controller.dto.home.RecommendProductDTO;
import com.example.domain.marketing.Marketing;
import com.example.enums.AdvertiseType;
import com.example.enums.MarketingPlacement;
import com.example.enums.MarketingType;
import com.example.repository.marketing.MarketingRepository;
import com.example.service.product.RecommendProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Transactional
@Service
public class HomeDisplayService {

    public static final int ITEM_PER_ROW = 3;
    private final MarketingRepository marketingRepository;
    private final RecommendProductService recommendProductService;

    public HomeDisplayService(MarketingRepository marketingRepository, RecommendProductService recommendProductService) {
        this.marketingRepository = marketingRepository;
        this.recommendProductService = recommendProductService;
    }

    public HomeDisplayDTO displayHome() {
        // 온사이트 마케팅 배너
        Optional<Marketing> marketingOptional = marketingRepository.findFirstByMarketingTypeAndMarketingPlacementAndAdvertiseType(
                MarketingType.ON_SITE, MarketingPlacement.HOME_BANNER, AdvertiseType.BANNER
        );

        String mainImageBannerSrc = Strings.EMPTY;
        if (marketingOptional.isPresent()) {
            mainImageBannerSrc = marketingOptional.get().getAdvertiseValue();
        }
        log.debug("Main Image Banner: {}", mainImageBannerSrc);

        // 추천 상품 목록
        List<RecommendProductDTO> recommendProductDTOS = recommendProductService.recommend();
        List<List<RecommendProductDTO>> recommendProductPartition = ListUtils.partition(recommendProductDTOS, ITEM_PER_ROW);

        log.debug("추천 상품 목록 : {}", recommendProductPartition);

        return new HomeDisplayDTO(mainImageBannerSrc, recommendProductPartition);
    }
}
