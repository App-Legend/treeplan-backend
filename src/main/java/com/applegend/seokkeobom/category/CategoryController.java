package com.applegend.seokkeobom.category;

import com.applegend.seokkeobom.common.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {
    private final CatalogService catalog;
    private final WordTreeService trees;

    public CategoryController(CatalogService catalog, WordTreeService trees) {
        this.catalog = catalog;
        this.trees = trees;
    }

    @GetMapping("/categories")
    public List<CatalogService.CategoryDto> list() {
        return catalog.listCategories();
    }

    /** 뿌리(하위 카테고리) 3개와, sessionId가 있으면 그 세션에서 뻗은 단어까지 */
    @GetMapping("/categories/{id}/words")
    public CatalogService.WordTreeDto words(@PathVariable Long id, @RequestParam(required = false) Long sessionId) {
        Long userId = sessionId == null ? null : CurrentUser.id();
        return trees.tree(id, sessionId, userId);
    }

    /** 뿌리 또는 단어를 눌러 AI로 가지 뻗기 */
    @PostMapping("/words/{id}/expand")
    public WordTreeService.ExpandResponse expand(@PathVariable Long id, @RequestBody WordTreeService.ExpandRequest req) {
        return trees.expand(id, CurrentUser.id(), req);
    }
}
