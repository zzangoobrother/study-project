package com.example.controller.search;

import com.example.controller.cart.CartModelAttributeKeys;
import com.example.controller.dto.search.SearchResultDTO;
import com.example.domain.customer.CustomerDetail;
import com.example.service.cart.CartService;
import com.example.service.search.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
public class SearchController {

    private final static int ITEM_PER_ROW = 3;

    private final SearchService searchService;
    private final CartService cartService;

    public SearchController(SearchService searchService, CartService cartService) {
        this.searchService = searchService;
        this.cartService = cartService;
    }

    @PostMapping(value = "/search")
    public String search(@AuthenticationPrincipal CustomerDetail customerDetail, @RequestParam String keyword, Model model) {
        log.info("Search Keyword, {}", keyword);
        List<SearchResultDTO> searchResults = this.searchService.search(keyword);

        log.info(">>> Search Result, {}", searchResults);
        List<List<SearchResultDTO>> searchResultPartition = ListUtils.partition(searchResults, ITEM_PER_ROW);

        if (Objects.nonNull(customerDetail)) {
            int countCartProduct = cartService.countCartProduct(customerDetail.getCustomer().getCustomerId());
            model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, countCartProduct);
        }

        model.addAttribute("keyword", keyword);
        model.addAttribute("searchResultPartition", searchResultPartition);

        return "/search/search-results";
    }
}
