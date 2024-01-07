package com.project.adminboard.controller;

import com.project.adminboard.dto.response.ArticleResponse;
import com.project.adminboard.service.ArticleManagementService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/management/articles")
@Controller
public class ArticleManagementController {

    private ArticleManagementService articleManagementService;

    @GetMapping
    public String articles(@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable, Model model) {
        model.addAttribute("articles", articleManagementService.getArticles().stream().map(ArticleResponse::withoutContent).toList());

        return "management/articles";
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ArticleResponse article(@PathVariable Long id) {
        return ArticleResponse.withContent(articleManagementService.getArticle(id));
    }

    @PostMapping("/{id}")
    public String deleteArticle(@PathVariable Long id) {
        articleManagementService.deleteArticle(id);

        return "redirect:/management/articles";
    }
}
