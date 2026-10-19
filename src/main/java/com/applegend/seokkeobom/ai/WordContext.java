package com.applegend.seokkeobom.ai;

import java.util.List;

/**
 * AI 단어 생성(화살표 ⑥ 다)에 필요한 입력 묶음.
 * path는 뿌리(하위 카테고리)부터 지금 뻗을 단어까지의 경로, existing은 이 트리에 이미 있는 단어(중복 금지용).
 * count는 AI에게 받을 후보 수, angles는 매번 무작위로 고른 연상 관점(단어가 세션마다 비슷해지지 않게).
 */
public record WordContext(
        String categorySlug,
        String categoryName,
        String rootText,
        List<String> path,
        List<String> existing,
        int count,
        List<String> angles) {

    /** 지금 가지를 뻗을 단어 (뿌리에서 뻗으면 뿌리 이름) */
    public String parentText() {
        return path.isEmpty() ? rootText : path.get(path.size() - 1);
    }
}
