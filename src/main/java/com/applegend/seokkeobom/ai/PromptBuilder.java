package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.TemplateItem;

import java.util.stream.Collectors;

/** 양식별 지시문(프롬프트). 팀이 직접 다듬어야 하는 핵심 파일이다. (위험 2 대응) */
public final class PromptBuilder {
    private PromptBuilder() {}

    public static final String SYSTEM = """
            너는 대학생의 아이디어 발상을 돕는 기획 파트너다.
            - 반드시 한국어로 답한다.
            - 사용자가 단어 트리에서 고른 단어 3개를 '모두' 의미 있게 연결한다. 하나라도 빠지거나 장식처럼만 쓰면 실패다.
            - 누구나 떠올릴 법한 진부한 결과(뻔한 앱, 흔한 캠페인, 이미 유명한 메뉴)는 피하고, 단어가 만나서 생기는 의외의 지점을 살린다.
            - 결과는 검증된 정답이 아니라 사용자가 다듬을 '아이디어 초안'이다. 사실처럼 단정하지 말고, 수치·출처를 지어내지 않는다.
            - 요청한 JSON 형식만 출력한다. 코드 블록이나 다른 설명을 붙이지 않는다.
            """;

    public static String ideaPrompt(GenerationContext ctx) {
        return """
                분야: %s
                사용자가 고른 단어 3개: %s

                세 단어를 모두 연결해 이 분야의 새로운 아이디어 하나를 만들어라.
                아래 JSON 형식으로만 답하라.
                {"title": "20자 안팎의 아이디어 이름", "summary": "무엇을 누구에게 어떻게 하는지, 세 단어가 어디에 쓰였는지 드러나는 2~3문장"}
                """.formatted(ctx.categoryName(), String.join(", ", ctx.words()));
    }

    public static String resultPrompt(GenerationContext ctx) {
        String items = ctx.items().stream()
                .map(i -> "- \"%s\" (%s): %s".formatted(i.key(), i.label(), i.description()))
                .collect(Collectors.joining("\n"));
        String keys = ctx.items().stream().map(i -> "\"" + i.key() + "\": \"...\"").collect(Collectors.joining(", "));
        String feedback = (ctx.feedback() == null || ctx.feedback().isBlank())
                ? ""
                : "\n사용자 보완 요청(반드시 반영): " + ctx.feedback().strip() + "\n";
        return """
                분야: %s
                사용한 단어 3개: %s
                아이디어 제목: %s
                아이디어 요약: %s

                [분야별 작성 지침]
                %s

                아래 항목을 순서대로, 바로 실행에 옮길 수 있을 만큼 구체적으로 채워라.
                각 값은 문자열이며 줄바꿈(\\n)과 '- ' 목록을 써도 된다.
                %s
                %s
                JSON 형식으로만 답하라: {%s}
                """.formatted(ctx.categoryName(), String.join(", ", ctx.words()), ctx.ideaTitle(), ctx.ideaSummary(),
                ctx.templateInstruction(), items, feedback, keys);
    }

    /** 단어 트리 가지 뻗기용 지시문. 빠른 응답이 중요해 짧게 유지한다. */
    public static final String WORD_SYSTEM = """
            너는 대학생의 아이디어 발상을 돕는 연상 단어 생성기다.
            - 반드시 한국어로 답한다.
            - 요청한 분야와 하위 카테고리 범위 안의 단어만 만든다. 다른 분야 단어를 섞지 않는다.
            - 요청한 JSON 형식만 출력한다. 코드 블록이나 다른 설명을 붙이지 않는다.
            """;

    public static String wordsPrompt(WordContext ctx) {
        String existing = ctx.existing().isEmpty() ? "(없음)" : String.join(", ", ctx.existing());
        String path = ctx.path().isEmpty() ? ctx.rootText() : ctx.rootText() + " > " + String.join(" > ", ctx.path());
        return """
                분야: %s
                하위 카테고리: %s
                지금 가지를 뻗을 단어: %s (경로: %s)
                이미 트리에 있는 단어(다시 쓰지 말 것): %s

                '%s'에서 떠올릴 수 있는 단어 %d개를 만들어라.
                - 모두 '%s' 분야의 '%s' 하위 카테고리에 속해야 한다.
                - 1~3어절, 12자 이내의 명사 또는 명사구.
                - 서로 다른 방향으로 다양하게. 너무 뻔한 단어만 고르지 말고, 아이디어의 출발점이 될 만한 구체적인 단어를 섞는다.
                JSON 형식으로만 답하라: {"words": ["...", "..."]}
                """.formatted(ctx.categoryName(), ctx.rootText(), ctx.parentText(), path, existing,
                ctx.parentText(), ctx.count(), ctx.categoryName(), ctx.rootText());
    }
}
