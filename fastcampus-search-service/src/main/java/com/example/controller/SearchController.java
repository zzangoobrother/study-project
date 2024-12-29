package com.example.controller;

import com.example.dto.ProductTagsDto;
import com.example.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search/addTagCache")
    public void addTagCache(@RequestBody ProductTagsDto dto) {
        searchService.addTagCache(dto.productId(), dto.tags());
    }

    @PostMapping("/search/removeTagCache")
    public void removeTagCache(@RequestBody ProductTagsDto dto) {
        searchService.removeTagCache(dto.productId(), dto.tags());
    }

    @GetMapping("/search/tags/{tag}/productIds")
    public List<Long> getTagProductIds(@PathVariable String tag) {
        return searchService.getProductIdsByTag(tag);
    }
}
