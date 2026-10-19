package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.Subcategories;
import com.applegend.seokkeobom.category.TemplateItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GEMINI_API_KEY가 없을 때 쓰는 예시(시연용) 생성기.
 * 위험 3(외부 API 장애·지연)에 대비해 발표 중에도 흐름을 끝까지 보여 줄 수 있게 한다.
 * 결과에는 '[예시 모드]' 표시가 붙어 실제 AI 결과로 오해하지 않게 한다.
 */
public class MockAiClient implements AiClient {
    private static final String TAG = "[예시 모드] ";

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public IdeaDraft generateIdea(GenerationContext ctx) {
        pause();
        List<String> w = ctx.words();
        String a = w.get(0), b = w.get(1), c = w.get(2);
        return switch (ctx.categorySlug()) {
            case "food" -> new IdeaDraft(c + "에 어울리는 " + a + " " + b + " 한입",
                    TAG + "'" + a + "'의 맛을 중심에 두고 '" + b + "'의 식감·조리 방식을 더해, '" + c
                            + "' 상황에서 간편하게 즐기는 메뉴입니다. 세 단어가 각각 맛·조리·먹는 장면을 맡습니다.");
            case "program" -> new IdeaDraft(a + "를 위한 " + b + " " + c + " 도우미",
                    TAG + "'" + a + "'가 겪는 '" + c + "' 문제를 '" + b
                            + "' 기능으로 줄여 주는 서비스입니다. 대상·기술·문제가 한 흐름으로 이어지도록 구성했습니다.");
            case "contest" -> new IdeaDraft(c + "에서 여는 " + a + " × " + b + " 프로젝트",
                    TAG + "'" + a + "' 주제를 '" + b + "'의 눈높이에서 다시 해석하고, '" + c
                            + "'을(를) 실행 방식이자 무대로 삼는 공모전 기획입니다.");
            default -> new IdeaDraft(String.join(" × ", w), TAG + "세 단어를 연결한 예시 아이디어입니다.");
        };
    }

    @Override
    public Map<String, String> generateResult(GenerationContext ctx) {
        pause();
        List<String> w = ctx.words();
        String a = w.get(0), b = w.get(1), c = w.get(2);
        Map<String, String> body = new LinkedHashMap<>();
        for (TemplateItem item : ctx.items()) {
            body.put(item.key(), sample(ctx.categorySlug(), item.key(), a, b, c, ctx));
        }
        return body;
    }

    /** 예시 모드: 뿌리별 예시 단어 중 트리에 아직 없는 것을 순서대로 돌려준다. */
    @Override
    public List<String> generateWords(WordContext ctx) {
        Set<String> taken = new HashSet<>(ctx.existing());
        taken.addAll(ctx.path());
        List<String> words = new ArrayList<>();
        List<String> pool = new ArrayList<>(Subcategories.sampleWords(ctx.categorySlug(), ctx.rootText()));
        Collections.shuffle(pool); // 예시 모드도 매번 다른 순서
        for (String w : pool) {
            if (words.size() >= ctx.count()) break;
            if (!taken.contains(w)) words.add(w);
        }
        if (words.isEmpty()) {
            throw new AiException("예시 모드에서 쓸 단어가 더 없습니다. 다른 하위 카테고리에서 뻗어 보세요.");
        }
        return words;
    }

    private static String sample(String slug, String key, String a, String b, String c, GenerationContext ctx) {
        String fb = (ctx.feedback() == null || ctx.feedback().isBlank()) ? "" : "\n- 보완 요청 반영: " + ctx.feedback().strip();
        return switch (slug + ":" + key) {
            case "food:concept" -> TAG + c + " 때 한 손으로 들고 먹는 " + a + " 풍미의 " + b + " 간식" + fb;
            case "food:ingredients" -> "- " + a + " 2큰술 (주재료)\n- 쌀과자 또는 크래커 1컵\n- 견과류 한 줌\n- 꿀 1큰술";
            case "food:steps" -> "1. " + a + "와(과) 꿀을 약불에 데워 섞는다.\n2. 부순 쌀과자·견과와 버무린다.\n3. " + b
                    + " 식감이 살도록 한입 크기로 눌러 30분 굳힌다.\n4. " + c + "에 들고 나가기 좋게 개별 포장한다.";
            case "food:result_caution" -> "- 예상 결과: 향은 산뜻하고 식감은 가벼운 한입 간식\n- 주의: 습기에 약하니 밀폐 보관, 견과류 알레르기 표시";
            case "program:user_problem" -> "- 대상: " + a + "\n- 문제: " + c + " 때문에 일상 관리가 자주 무너진다." + fb;
            case "program:hypothesis" -> b + " 기능으로 필요한 순간에만 짧게 개입하면 " + c + " 문제가 줄어들 것이다.";
            case "program:features" -> "- " + b + " 기반 핵심 알림\n- 하루 요약 카드\n- 한 번 탭으로 기록";
            case "program:flow" -> "1. 첫 실행에서 상황을 3가지 질문으로 설정\n2. 필요한 순간에 " + b + " 안내 수신\n3. 탭 한 번으로 완료 기록\n4. 주간 요약 확인";
            case "program:mvp" -> "- 포함: 설정·알림·기록\n- 제외: 소셜 기능, 결제\n- 검증: " + a + " 5명에게 1주일 사용 테스트";
            case "contest:interpretation" -> a + "을(를) 거창한 구호가 아니라 일상 공간에서 체감하는 문제로 해석한다." + fb;
            case "contest:target_problem" -> "- 대상: " + b + "\n- 문제: " + a + " 관련 정보가 있어도 참여할 계기가 없다.";
            case "contest:concept" -> c + "을(를) 무대로 " + b + "이(가) 직접 참여하는 " + a + " 체험 프로젝트";
            case "contest:differentiation" -> "- 기존 캠페인은 홍보에 그치지만, 이 기획은 " + c + "에서 직접 해 보게 한다.\n- 결과물이 공간에 남아 지속된다.";
            case "contest:execution" -> "1. 1주차: " + c + " 후보지 조사\n2. 2~3주차: " + b + " 대상 사전 인터뷰\n3. 4주차: 시범 운영\n4. 5주차: 참여 데이터 정리·보고";
            default -> {
                if (key.equals("reason")) {
                    yield "'" + a + "'은(는) 핵심 소재, '" + b + "'은(는) 방식·성격, '" + c + "'은(는) 사용 장면을 맡아 서로 비어 있는 부분을 채웁니다.";
                }
                yield TAG + "예시 내용";
            }
        };
    }

    private static void pause() {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
