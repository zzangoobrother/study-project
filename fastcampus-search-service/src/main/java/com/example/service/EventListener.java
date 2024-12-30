package com.example.service;

import com.example.dto.ProductTagsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventListener {

    private final SearchService searchService;

    public EventListener(SearchService searchService) {
        this.searchService = searchService;
    }

    @KafkaListener(topics = "product_tags_added")
    public void consumeTagAdded(String dto) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ProductTagsDto dto1 = mapper.readValue(dto, ProductTagsDto.class);
        searchService.addTagCache(dto1.productId(), dto1.tags());
    }

    @KafkaListener(topics = "product_tags_removed")
    public void consumeTagRemoved(String dto) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ProductTagsDto dto1 = mapper.readValue(dto, ProductTagsDto.class);
        searchService.removeTagCache(dto1.productId(), dto1.tags());
    }
}
