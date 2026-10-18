package com.applegend.seokkeobom.ai;

import java.util.List;

/**
 * AI 단어 생성(화살표 ⑥ 다)에 필요한 입력 묶음.
 * path는 뿌리(하위 카테고리)부터 지금 뻗을 단어까지의 경로, existing은 이 트리에 이미 있는 단어(중복 금지용).
 */
public record WordContext(
        String categorySlug,
        String categoryName,
        String rootText,
        List<String> path,
        List<String> existing,
        int count) {

    /** 지금 가지를 뻗을 단어 (뿌리에서 뻗으면 뿌리 이름) */
    public String parentText() {
        return path.isEmpty() ? rootText : path.get(path.size() - 1);
    }
}
