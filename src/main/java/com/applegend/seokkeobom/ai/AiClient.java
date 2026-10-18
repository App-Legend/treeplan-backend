package com.applegend.seokkeobom.ai;

import java.util.List;
import java.util.Map;

public interface AiClient {
    /** (가) 단어 3개 + 카테고리 → 아이디어 제목과 2~3문장 요약 */
    IdeaDraft generateIdea(GenerationContext ctx);

    /** (나) 아이디어 + 카테고리 양식 → 양식 항목 key별 본문 */
    Map<String, String> generateResult(GenerationContext ctx);

    /** (다) 하위 카테고리·상위 단어 → 관련 단어 목록 (단어 트리 가지 뻗기) */
    List<String> generateWords(WordContext ctx);

    String mode();

    /** 단어 생성에 쓰는 모델 표시 (따로 두지 않으면 mode()와 같음) */
    default String wordMode() {
        return mode();
    }
}
