package com.applegend.seokkeobom.ai;

import java.util.Map;

public interface AiClient {
    /** (가) 단어 3개 + 카테고리 → 아이디어 제목과 2~3문장 요약 */
    IdeaDraft generateIdea(GenerationContext ctx);

    /** (나) 아이디어 + 카테고리 양식 → 양식 항목 key별 본문 */
    Map<String, String> generateResult(GenerationContext ctx);

    String mode();
}
