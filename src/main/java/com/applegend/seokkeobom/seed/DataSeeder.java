package com.applegend.seokkeobom.seed;

import com.applegend.seokkeobom.category.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MVP 고정 카테고리 3개, 분야별 단어 트리, 절차 템플릿(결과물 양식)을 처음 실행할 때 넣는다.
 * 단어를 늘리거나 고치려면 이 파일을 수정하고 DB의 categories·word_nodes·result_templates를 비운 뒤 다시 실행한다.
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
        if (categories.count() > 0) return;
        seed("음식 개발", "food", 1, FOOD_ITEMS, FOOD_INSTRUCTION, FOOD_WORDS);
        seed("프로그램 개발", "program", 2, PROGRAM_ITEMS, PROGRAM_INSTRUCTION, PROGRAM_WORDS);
        seed("공모전 기획", "contest", 3, CONTEST_ITEMS, CONTEST_INSTRUCTION, CONTEST_WORDS);
        log.info("기본 카테고리·단어 풀·결과물 양식을 넣었습니다.");
    }

    private void seed(String name, String slug, int order, List<TemplateItem> items, String instruction,
                      Map<String, List<String>> groups) throws Exception {
        ResultTemplate template = templates.save(new ResultTemplate(objectMapper.writeValueAsString(items), instruction));
        Category category = categories.save(new Category(name, slug, template.getId(), order));
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            WordNode group = wordNodes.save(new WordNode(category.getId(), null, g.getKey(), WordNode.GROUP_DEPTH));
            for (String w : g.getValue()) {
                wordNodes.save(new WordNode(category.getId(), group.getId(), w, WordNode.WORD_DEPTH));
            }
        }
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

    static final Map<String, List<String>> FOOD_WORDS = ordered(
            "재료", List.of("유자", "흑임자", "감태", "들기름", "무화과", "고구마", "쑥", "오미자", "버터", "표고버섯", "단호박", "레몬그라스"),
            "조리법", List.of("훈연", "발효", "수비드", "튀김", "절임", "동결건조", "숙성", "캐러멜라이징", "찜", "그릴"),
            "감각", List.of("바삭함", "쫀득함", "새콤함", "스모키함", "청량감", "고소함", "매콤함", "부드러움", "톡 쏘는 맛", "따뜻함"),
            "상황", List.of("산책", "야식", "캠핑", "시험기간", "출근길", "비 오는 날", "혼밥", "생일", "소풍", "새벽"));

    static final Map<String, List<String>> PROGRAM_WORDS = ordered(
            "사용자", List.of("자취생", "반려인", "고령자", "신입생", "프리랜서", "운동 초보", "여행자", "초등학생 부모", "야간 근무자", "유학생"),
            "기술", List.of("위치 기반", "음성 인식", "카메라 인식", "푸시 알림", "지도", "캘린더 연동", "챗봇", "게임화", "오프라인 모드", "공유 링크"),
            "문제", List.of("미루기", "음식 낭비", "고립감", "정보 과잉", "분실", "층간 소음", "수면 부족", "낯선 길", "정리 안 됨", "약속 잊음"),
            "맥락", List.of("출퇴근", "시험기간", "이사", "장보기", "주말", "동아리", "아르바이트", "병원 방문", "명절", "장마철"));

    static final Map<String, List<String>> CONTEST_WORDS = ordered(
            "주제", List.of("탄소중립", "지역 소멸", "1인 가구", "디지털 격차", "청년 주거", "전통시장", "반려동물", "고령화", "생활 안전", "동네 상권"),
            "대상", List.of("어르신", "외국인 주민", "청소년", "대학생", "소상공인", "장애인", "관광객", "다문화 가정", "직장인", "어린이"),
            "방식", List.of("캠페인", "플랫폼", "굿즈", "축제", "교육 프로그램", "숏폼 영상", "지도 제작", "공공 디자인", "크라우드펀딩", "체험 키트"),
            "장소", List.of("버스정류장", "폐교", "골목", "도서관", "편의점", "하천", "옥상", "운동장", "시장 골목", "지하철역"));

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> ordered(Object... kv) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], (List<String>) kv[i + 1]);
        return map;
    }
}
