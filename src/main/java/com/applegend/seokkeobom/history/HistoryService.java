package com.applegend.seokkeobom.history;

import com.applegend.seokkeobom.category.CatalogService;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.common.GenerationStatus;
import com.applegend.seokkeobom.draw.Draw;
import com.applegend.seokkeobom.draw.DrawRepository;
import com.applegend.seokkeobom.idea.Idea;
import com.applegend.seokkeobom.idea.IdeaRepository;
import com.applegend.seokkeobom.result.Result;
import com.applegend.seokkeobom.result.ResultRepository;
import com.applegend.seokkeobom.session.IdeaSession;
import com.applegend.seokkeobom.session.SessionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 히스토리(화면 7)는 별도 덩어리가 아니라 결과물·보관 아이디어를 시간순으로 모아 보여 준 것이다. */
@Service
public class HistoryService {
    private static final int PAGE_SIZE = 20;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final ResultRepository results;
    private final IdeaRepository ideas;
    private final DrawRepository draws;
    private final SessionService sessions;
    private final CatalogService catalog;

    public HistoryService(ResultRepository results, IdeaRepository ideas, DrawRepository draws,
                          SessionService sessions, CatalogService catalog) {
        this.results = results;
        this.ideas = ideas;
        this.draws = draws;
        this.sessions = sessions;
        this.catalog = catalog;
    }

    public enum ItemType { RESULT, IDEA }

    public record HistoryItem(ItemType type, Long id, Long sessionId, String sessionName, boolean teamSession,
                              String title, String summary, List<String> words, Long categoryId, String categoryName,
                              Instant at, GenerationStatus status, Long resultId, boolean canDelete) {}

    public record HistoryPage(List<HistoryItem> items, String nextCursor) {}

    public HistoryPage list(Long userId, String rawQuery, Long categoryId, String cursor) {
        List<Long> sessionIds = sessions.sessionIdsOf(userId);
        if (sessionIds.isEmpty()) return new HistoryPage(List.of(), null);

        Instant before = decodeCursor(cursor);
        String q = rawQuery == null || rawQuery.isBlank() ? "" : "%" + rawQuery.strip().toLowerCase(Locale.ROOT) + "%";
        long cat = categoryId == null ? -1L : categoryId;
        PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);

        List<Result> rs = results.findForHistory(sessionIds, before, cat, q, page);
        List<Idea> is = ideas.findBookmarkedForHistory(sessionIds, before, cat, q, page);

        Map<Long, String> categoryNames = catalog.listCategories().stream()
                .collect(Collectors.toMap(CatalogService.CategoryDto::id, CatalogService.CategoryDto::name));
        Set<Long> touchedSessions = new HashSet<>();
        rs.forEach(r -> touchedSessions.add(r.getSessionId()));
        is.forEach(i -> touchedSessions.add(i.getSessionId()));
        Map<Long, IdeaSession> sessionMap = sessions.findAll(touchedSessions);
        Map<Long, Boolean> team = new HashMap<>();
        touchedSessions.forEach(id -> team.put(id, sessions.memberCount(id) > 1));
        Map<Long, Boolean> owner = new HashMap<>();
        touchedSessions.forEach(id -> owner.put(id, sessions.isOwner(id, userId)));
        Map<Long, Draw> drawMap = draws.findAllById(is.stream().map(Idea::getDrawId).toList()).stream()
                .collect(Collectors.toMap(Draw::getId, Function.identity()));
        Map<Long, Long> resultByIdea = new HashMap<>();
        for (Idea i : is) {
            results.findByIdeaId(i.getId()).filter(r -> !r.isDeleted()).ifPresent(r -> resultByIdea.put(i.getId(), r.getId()));
        }

        List<HistoryItem> merged = new ArrayList<>();
        for (Result r : rs) {
            merged.add(new HistoryItem(ItemType.RESULT, r.getId(), r.getSessionId(), sessionName(sessionMap, r.getSessionId()),
                    team.getOrDefault(r.getSessionId(), false), r.getTitle(), null, r.getWords(), r.getCategoryId(),
                    r.getCategoryName(), r.getCreatedAt(), r.getStatus(), r.getId(),
                    owner.getOrDefault(r.getSessionId(), false)));
        }
        for (Idea i : is) {
            Draw d = drawMap.get(i.getDrawId());
            merged.add(new HistoryItem(ItemType.IDEA, i.getId(), i.getSessionId(), sessionName(sessionMap, i.getSessionId()),
                    team.getOrDefault(i.getSessionId(), false), i.getTitle(), i.getSummary(),
                    d == null ? List.of() : d.getWords(), i.getCategoryId(), categoryNames.getOrDefault(i.getCategoryId(), ""),
                    i.getBookmarkedAt(), i.getStatus(), resultByIdea.get(i.getId()), false));
        }
        merged.sort(Comparator.comparing(HistoryItem::at).reversed());

        boolean hasMore = merged.size() > PAGE_SIZE;
        List<HistoryItem> pageItems = hasMore ? merged.subList(0, PAGE_SIZE) : merged;
        String next = hasMore ? encodeCursor(pageItems.get(pageItems.size() - 1).at()) : null;
        return new HistoryPage(new ArrayList<>(pageItems), next);
    }

    private static String sessionName(Map<Long, IdeaSession> map, Long id) {
        IdeaSession s = map.get(id);
        return s == null ? "" : s.getName();
    }

    private static String encodeCursor(Instant at) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(at.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Instant decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return FAR_FUTURE;
        try {
            return Instant.parse(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_CURSOR", "목록 위치 정보가 올바르지 않습니다. 새로고침해 주세요.");
        }
    }
}
