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
    public record WordDto(Long id, String text, List<WordDto> children) {}
    /** 뿌리(하위 카테고리). wordCount는 이 세션에서 뻗은 단어 수, remaining은 더 뻗을 수 있는 수 */
    public record WordGroupDto(Long id, String text, int wordCount, int remaining, List<WordDto> children) {}
    public record WordTreeDto(Long categoryId, String name, int wordCount, int maxWordsPerRoot, List<WordGroupDto> groups) {}

    public List<CategoryDto> listCategories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    public Category requireCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."));
    }

    public WordNode requireNode(Long id) {
        return wordNodes.findById(id)
                .orElseThrow(() -> ApiException.notFound("WORD_NOT_FOUND", "단어를 찾을 수 없습니다. 트리를 새로고침해 주세요."));
    }

    /** 뿌리 3개 + (sessionId가 있으면) 그 세션에서 뻗은 단어를 트리로 묶는다. */
    public WordTreeDto wordTree(Long categoryId, Long sessionId) {
        Category category = requireCategory(categoryId);
        List<WordNode> roots = wordNodes.findByCategoryIdAndSessionIdIsNullOrderByIdAsc(categoryId).stream()
                .filter(WordNode::isRoot).toList();
        List<WordNode> words = sessionId == null ? List.of()
                : wordNodes.findByCategoryIdAndSessionIdOrderByIdAsc(categoryId, sessionId);
        Map<Long, List<WordNode>> byParent = new HashMap<>();
        for (WordNode w : words) {
            byParent.computeIfAbsent(w.getParentId(), k -> new ArrayList<>()).add(w);
        }
        List<WordGroupDto> groups = new ArrayList<>();
        for (WordNode root : roots) {
            int count = (int) words.stream().filter(w -> root.getId().equals(w.getRootId())).count();
            groups.add(new WordGroupDto(root.getId(), root.getText(), count,
                    Math.max(0, WordNode.MAX_WORDS_PER_ROOT - count), childrenOf(root.getId(), byParent)));
        }
        return new WordTreeDto(category.getId(), category.getName(), words.size(), WordNode.MAX_WORDS_PER_ROOT, groups);
    }

    private static List<WordDto> childrenOf(Long parentId, Map<Long, List<WordNode>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(n -> new WordDto(n.getId(), n.getText(), childrenOf(n.getId(), byParent)))
                .toList();
    }

    /** 뽑기 대상: 이 세션의 트리에 있는 단어(뿌리 제외) */
    public List<String> wordPool(Long categoryId, Long sessionId) {
        return wordNodes.findByCategoryIdAndSessionIdOrderByIdAsc(categoryId, sessionId).stream()
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
