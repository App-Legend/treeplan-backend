package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.TemplateItem;

import java.util.List;

/** AI 호출(화살표 ⑥)에 필요한 입력 묶음 */
public record GenerationContext(
        String categorySlug,
        String categoryName,
        List<String> words,
        String ideaTitle,
        String ideaSummary,
        List<TemplateItem> items,
        String templateInstruction,
        String feedback) {
}
