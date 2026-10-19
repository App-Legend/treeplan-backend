package com.applegend.seokkeobom.category;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 큰 카테고리마다 팀이 정한 고정 하위 카테고리 3개(단어 트리의 뿌리).
 * 하위 카테고리 이름은 팀 회의 확정 전까지 임시 값이다. 바꾸면 다음 실행 때 DataSeeder가 뿌리를 다시 맞춘다.
 *
 * 각 뿌리의 단어 목록은 GEMINI_API_KEY가 없을 때(예시 모드) AI 대신 쓰는 예시 단어다.
 * 실제 모드에서는 AI가 단어를 만들며, 뿌리마다 가지 뻗기는 최대 {@link WordNode#MAX_EXPANDS_PER_ROOT}번이다.
 */
public final class Subcategories {
    private Subcategories() {}

    public static final Map<String, Map<String, List<String>>> BY_CATEGORY = new LinkedHashMap<>();

    static {
        BY_CATEGORY.put("food", ordered(
                "재료", List.of("유자", "흑임자", "감태", "들기름", "무화과", "고구마", "쑥", "오미자", "버터", "표고버섯",
                        "단호박", "레몬그라스", "밤", "대추", "연근", "메밀", "곶감", "깻잎", "황태", "귀리"),
                "조리법", List.of("훈연", "발효", "수비드", "튀김", "절임", "동결건조", "숙성", "캐러멜라이징", "찜", "그릴",
                        "졸임", "데치기", "에어프라이", "저온 건조", "빙수", "무스", "콩피", "크럼블", "냉침", "토치"),
                "먹는 상황", List.of("산책", "야식", "캠핑", "시험기간", "출근길", "비 오는 날", "혼밥", "생일", "소풍", "새벽",
                        "운동 후", "영화관", "집들이", "명절", "해장", "도서관", "기차 여행", "피크닉", "야구장", "첫 데이트")));
        BY_CATEGORY.put("program", ordered(
                "사용자", List.of("자취생", "반려인", "고령자", "신입생", "프리랜서", "운동 초보", "여행자", "초등학생 부모",
                        "야간 근무자", "유학생", "취준생", "1인 창업자", "캠핑 초보", "동아리 회장", "식물 집사", "수험생",
                        "배달 라이더", "신혼부부", "시각장애인", "교환학생"),
                "문제", List.of("미루기", "음식 낭비", "고립감", "정보 과잉", "분실", "층간 소음", "수면 부족", "낯선 길",
                        "정리 안 됨", "약속 잊음", "충동구매", "작심삼일", "공과금 연체", "중고거래 사기", "집안일 분담",
                        "무임승차", "냉장고 관리", "복약 누락", "길고양이 돌봄", "일정 충돌"),
                "기술", List.of("위치 기반", "음성 인식", "카메라 인식", "푸시 알림", "지도", "캘린더 연동", "챗봇", "게임화",
                        "오프라인 모드", "공유 링크", "QR 코드", "위젯", "웨어러블", "바코드 스캔", "실시간 채팅",
                        "추천 알고리즘", "타이머", "투표", "증강현실", "음성 메모")));
        BY_CATEGORY.put("contest", ordered(
                "주제", List.of("탄소중립", "지역 소멸", "1인 가구", "디지털 격차", "청년 주거", "전통시장", "반려동물", "고령화",
                        "생활 안전", "동네 상권", "일회용품", "저출생", "기후 위기", "문화유산", "정신 건강", "다문화 공존",
                        "보행 안전", "폐교 활용", "로컬 관광", "재난 대비"),
                "대상", List.of("어르신", "외국인 주민", "청소년", "대학생", "소상공인", "장애인", "관광객", "다문화 가정",
                        "직장인", "어린이", "경력 단절 여성", "귀농 청년", "택배 기사", "보호 종료 아동", "농촌 주민",
                        "은퇴자", "초보 운전자", "군 장병", "야간 노동자", "1인 창작자"),
                "실행 방식", List.of("캠페인", "플랫폼", "굿즈", "축제", "교육 프로그램", "숏폼 영상", "지도 제작", "공공 디자인",
                        "크라우드펀딩", "체험 키트", "팝업스토어", "보드게임", "전시", "웹툰", "라디오", "챌린지", "리빙랩",
                        "해커톤", "스탬프 투어", "오디오 가이드")));
    }

    /** 카테고리의 하위 카테고리 이름(뿌리) 목록 */
    public static List<String> rootsOf(String categorySlug) {
        return List.copyOf(BY_CATEGORY.getOrDefault(categorySlug, Map.of()).keySet());
    }

    /** 예시 모드에서 쓰는 뿌리별 단어 */
    public static List<String> sampleWords(String categorySlug, String rootText) {
        return BY_CATEGORY.getOrDefault(categorySlug, Map.of()).getOrDefault(rootText, List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> ordered(Object... kv) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], (List<String>) kv[i + 1]);
        return map;
    }
}
