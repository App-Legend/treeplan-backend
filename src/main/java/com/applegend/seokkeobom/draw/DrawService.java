package com.applegend.seokkeobom.draw;

import com.applegend.seokkeobom.category.CatalogService;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.session.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DrawService {
    public static final int WORD_COUNT = 3;
    /** 단어 트리에서 3개를 모두 직접 고정해 조합을 확정할 수 있다. */
    public static final int MAX_FIXED = WORD_COUNT;

    private final DrawRepository draws;
    private final CatalogService catalog;
    private final SessionService sessions;

    public DrawService(DrawRepository draws, CatalogService catalog, SessionService sessions) {
        this.draws = draws;
        this.catalog = catalog;
        this.sessions = sessions;
    }

    public record DrawRequest(List<Long> categoryIds, List<String> fixedWords, List<String> excludedWords) {}
    public record DrawResponse(Long drawId, Long categoryId, List<String> words, int attemptNo) {}

    @Transactional
    public DrawResponse draw(Long sessionId, Long userId, DrawRequest req) {
        sessions.requireActiveMemberAndTouch(sessionId, userId);

        List<Long> categoryIds = req.categoryIds() == null ? List.of() : req.categoryIds().stream().filter(Objects::nonNull).distinct().toList();
        if (categoryIds.isEmpty()) {
            throw ApiException.badRequest("CATEGORY_REQUIRED", "카테고리를 먼저 선택해 주세요.");
        }
        if (categoryIds.size() > 1) {
            // 비기능 요구사항(데이터): 단어는 카테고리별 단어 풀에서만 뽑아 다른 분야와 섞지 않는다.
            throw ApiException.badRequest("SINGLE_CATEGORY_ONLY", "한 번에 한 카테고리의 단어만 뽑을 수 있습니다.");
        }
        Long categoryId = categoryIds.get(0);
        catalog.requireCategory(categoryId);

        List<String> pool = catalog.wordPool(categoryId);
        Set<String> poolSet = new HashSet<>(pool);
        List<String> fixed = clean(req.fixedWords());
        Set<String> excluded = new LinkedHashSet<>(clean(req.excludedWords()));

        if (fixed.size() > MAX_FIXED) {
            throw ApiException.badRequest("TOO_MANY_FIXED", "고정할 수 있는 단어는 3개까지입니다.");
        }
        for (String f : fixed) {
            if (!poolSet.contains(f)) {
                throw ApiException.badRequest("FIXED_NOT_IN_CATEGORY", "'" + f + "'은(는) 선택한 카테고리의 단어가 아닙니다.");
            }
        }
        excluded.removeAll(fixed);

        List<String> candidates = new ArrayList<>();
        for (String w : pool) {
            if (!excluded.contains(w) && !fixed.contains(w)) candidates.add(w);
        }
        int need = WORD_COUNT - fixed.size();
        if (candidates.size() < need) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "POOL_TOO_SMALL",
                    "제외한 단어가 많아 3개를 채울 수 없습니다. 제외 목록을 줄이거나 다른 카테고리를 선택해 주세요.");
        }

        // 직전 조합과 똑같은 결과는 가능한 한 피한다 (위험 1: 조합이 금방 겹침)
        Set<String> previous = draws.findFirstBySessionIdAndUserIdOrderByIdDesc(sessionId, userId)
                .map(d -> (Set<String>) new HashSet<>(d.getWords())).orElse(Set.of());
        List<String> words = pick(fixed, candidates, need);
        for (int i = 0; i < 5 && new HashSet<>(words).equals(previous) && candidates.size() > need; i++) {
            words = pick(fixed, candidates, need);
        }

        int attemptNo = (int) draws.countBySessionId(sessionId) + 1;
        Draw saved = draws.save(new Draw(sessionId, userId, categoryId, fixed, new ArrayList<>(excluded), words, attemptNo));
        return new DrawResponse(saved.getId(), categoryId, saved.getWords(), saved.getAttemptNo());
    }

    public Draw require(Long drawId) {
        return draws.findById(drawId)
                .orElseThrow(() -> ApiException.notFound("DRAW_NOT_FOUND", "단어 조합을 찾을 수 없습니다. 다시 뽑아 주세요."));
    }

    private static List<String> pick(List<String> fixed, List<String> candidates, int need) {
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        List<String> words = new ArrayList<>(fixed);
        words.addAll(shuffled.subList(0, need));
        return words;
    }

    private static List<String> clean(List<String> in) {
        if (in == null) return List.of();
        return in.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
