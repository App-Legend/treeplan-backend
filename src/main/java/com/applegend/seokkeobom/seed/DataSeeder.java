package com.applegend.seokkeobom.seed;

import com.applegend.seokkeobom.category.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MVP 고정 카테고리 3개, 카테고리별 고정 하위 카테고리 3개(단어 트리의 뿌리), 절차 템플릿(결과물 양식)을 넣는다.
 * 뿌리 아래 단어는 미리 넣지 않고 세션마다 AI가 뻗는다(뿌리당 최대 20개).
 * 하위 카테고리 이름은 {@link Subcategories}에서 바꾼다. 바뀐 경우 다음 실행 때 공용 노드(뿌리)를 새 구성으로 맞춘다.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categories;
    private final WordNodeRepository wordNodes;
    private final ResultTemplateRepository templates;
    private final ObjectMapper objectMapper;

    public DataSeeder(CategoryRepository categories, WordNodeRepository wordNodes,
                      ResultTemplateRepository templates, ObjectMapper objectMapper) {
        this.categories = categories;
        this.wordNodes = wordNodes;
        this.templates = templates;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (categories.count() == 0) {
            seed("음식 개발", "food", 1, FOOD_ITEMS, FOOD_INSTRUCTION);
            seed("프로그램 개발", "program", 2, PROGRAM_ITEMS, PROGRAM_INSTRUCTION);
            seed("공모전 기획", "contest", 3, CONTEST_ITEMS, CONTEST_INSTRUCTION);
            log.info("기본 카테고리·결과물 양식을 넣었습니다.");
        }
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            syncRoots(c);
        }
    }

    private void seed(String name, String slug, int order, List<TemplateItem> items, String instruction) throws Exception {
        ResultTemplate template = templates.save(new ResultTemplate(objectMapper.writeValueAsString(items), instruction));
        categories.save(new Category(name, slug, template.getId(), order));
    }

    /**
     * 공용 노드(뿌리)가 Subcategories와 다르면(이전 버전의 분류 4개·미리 넣은 단어 등) 새 구성으로 바꾼다.
     * 세션에서 자란 단어는 건드리지 않는다. 뽑힌 단어는 추출(Draw)에 글자로 저장되므로 기존 기록은 그대로 남는다.
     */
    private void syncRoots(Category c) {
        List<String> wanted = Subcategories.rootsOf(c.getSlug());
        List<String> current = wordNodes.findByCategoryIdAndSessionIdIsNullOrderByIdAsc(c.getId()).stream()
                .map(n -> n.isRoot() ? n.getText() : "#" + n.getText())
                .toList();
        if (current.equals(wanted)) return;
        int removed = wordNodes.deleteSharedNodes(c.getId());
        for (String root : wanted) {
            wordNodes.save(WordNode.root(c.getId(), root));
        }
        log.info("'{}' 하위 카테고리를 {}(으)로 맞췄습니다. (이전 공용 노드 {}개 정리)", c.getName(), wanted, removed);
    }

    private static final TemplateItem REASON = new TemplateItem("reason", "조합 이유",
            "세 단어가 각각 어떤 역할(소재·방식·장면 등)로 쓰였고 왜 함께 묶였는지 2~3문장으로 설명");

    // ---- 음식 개발: 콘셉트 → 재료·분량 → 조리 순서 → 예상 결과·주의점 ----
    static final List<TemplateItem> FOOD_ITEMS = List.of(
            new TemplateItem("concept", "콘셉트", "어떤 메뉴인지, 누가 언제 먹는지 한두 문장"),
            new TemplateItem("ingredients", "재료와 분량", "1~2인분 기준 재료와 분량을 '- 재료 분량' 목록으로"),
            new TemplateItem("steps", "만드는 순서", "번호를 붙인 단계별 조리 순서, 불 세기와 시간 포함"),
            new TemplateItem("result_caution", "예상 결과와 주의점", "맛·식감 예상과 실패하기 쉬운 지점, 알레르기·위생 주의"),
            REASON);
    static final String FOOD_INSTRUCTION = """
            가정 주방에서 대학생이 실제로 만들 수 있는 수준으로 쓴다. 구하기 어려운 재료는 대체 재료를 함께 적는다.
            분량과 시간은 대략값이라도 숫자로 적는다. 건강 효능을 단정하지 않는다.""";

    // ---- 프로그램 개발: 사용자·문제 → 해결 가설 → 핵심 기능 → 사용 흐름 → MVP 범위 ----
    static final List<TemplateItem> PROGRAM_ITEMS = List.of(
            new TemplateItem("user_problem", "사용자와 문제", "대상 사용자 한 부류와 그들이 겪는 구체적 문제"),
            new TemplateItem("hypothesis", "해결 가설", "'~하면 ~가 줄어들 것이다' 형태의 검증 가능한 가설"),
            new TemplateItem("features", "핵심 기능", "꼭 필요한 기능 3개 이내를 목록으로"),
            new TemplateItem("flow", "사용 흐름", "첫 실행부터 목적 달성까지 번호를 붙인 화면 흐름"),
            new TemplateItem("mvp", "MVP 범위", "한 학기 안에 만들 것/뺄 것, 검증 방법"),
            REASON);
    static final String PROGRAM_INSTRUCTION = """
            학부생 3~4명이 한 학기에 만들 수 있는 웹/앱 규모로 제한한다. 기술 이름은 필요한 경우에만 쓴다.
            가설은 사용자 테스트로 확인할 수 있게 쓴다.""";

    // ---- 공모전 기획: 주제 해석 → 대상·문제 → 콘셉트 → 차별점 → 실행 단계 ----
    static final List<TemplateItem> CONTEST_ITEMS = List.of(
            new TemplateItem("interpretation", "주제 해석", "주제를 우리 팀은 어떤 관점으로 읽었는지"),
            new TemplateItem("target_problem", "대상과 문제", "누구의 어떤 문제를 다루는지"),
            new TemplateItem("concept", "콘셉트", "기획의 핵심을 한 문장 + 짧은 설명"),
            new TemplateItem("differentiation", "차별점", "기존 사례와 다른 점 2~3가지"),
            new TemplateItem("execution", "실행 단계", "준비부터 결과 정리까지 주차별 또는 단계별 계획"),
            REASON);
    static final String CONTEST_INSTRUCTION = """
            대학생 팀이 공모전 기획서 초안으로 바로 옮겨 쓸 수 있게 쓴다. 통계·수상 이력 등 사실을 지어내지 않고,
            필요한 근거는 '확인 필요'로 표시한다.""";
}
