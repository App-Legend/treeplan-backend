package com.applegend.seokkeobom.category;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CatalogService catalog;

    public CategoryController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<CatalogService.CategoryDto> list() {
        return catalog.listCategories();
    }

    @GetMapping("/{id}/words")
    public CatalogService.WordTreeDto words(@PathVariable Long id) {
        return catalog.wordTree(id);
    }
}
