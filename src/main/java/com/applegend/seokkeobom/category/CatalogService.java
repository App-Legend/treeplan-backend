package com.applegend.seokkeobom.category;

import com.applegend.seokkeobom.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final WordNodeRepository wordNodes;
    private final ResultTemplateRepository templates;
    private final ObjectMapper objectMapper;

    public CatalogService(CategoryRepository categories, WordNodeRepository wordNodes,
                          ResultTemplateRepository templates, ObjectMapper objectMapper) {
        this.categories = categories;
        this.wordNodes = wordNodes;
        this.templates = templates;
        this.objectMapper = objectMapper;
    }

    public record CategoryDto(Long id, String name, String slug) {}
    public record WordDto(Long id, String text) {}
    public record WordGroupDto(Long id, String text, List<WordDto> children) {}
    public record WordTreeDto(Long categoryId, String name, int wordCount, List<WordGroupDto> groups) {}

    public List<CategoryDto> listCategories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    public Category requireCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."));
    }

    public WordTreeDto wordTree(Long categoryId) {
        Category category = requireCategory(categoryId);
        List<WordNode> nodes = wordNodes.findByCategoryIdOrderByIdAsc(categoryId);
        Map<Long, List<WordDto>> childrenByParent = new HashMap<>();
        for (WordNode n : nodes) {
            if (n.getDepth() == WordNode.WORD_DEPTH && n.getParentId() != null) {
                childrenByParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>())
                        .add(new WordDto(n.getId(), n.getText()));
            }
        }
        List<WordGroupDto> groups = nodes.stream()
                .filter(n -> n.getDepth() == WordNode.GROUP_DEPTH)
                .map(g -> new WordGroupDto(g.getId(), g.getText(), childrenByParent.getOrDefault(g.getId(), List.of())))
                .toList();
        int count = (int) nodes.stream().filter(n -> n.getDepth() == WordNode.WORD_DEPTH).count();
        return new WordTreeDto(category.getId(), category.getName(), count, groups);
    }

    /** 뽑기 대상인 단어(트리의 잎) 목록 */
    public List<String> wordPool(Long categoryId) {
        return wordNodes.findByCategoryIdAndDepth(categoryId, WordNode.WORD_DEPTH).stream()
                .map(WordNode::getText)
                .distinct()
                .toList();
    }

    public ResultTemplate requireTemplate(Long templateId) {
        return templates.findById(templateId)
                .orElseThrow(() -> ApiException.conflict("TEMPLATE_MISSING", "이 카테고리의 결과물 양식이 준비되지 않았습니다."));
    }

    public List<TemplateItem> items(ResultTemplate template) {
        try {
            return objectMapper.readValue(template.getItemsJson(), new TypeReference<List<TemplateItem>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("결과물 양식 JSON을 읽을 수 없습니다: " + template.getId(), e);
        }
    }
}
