package com.applegend.seokkeobom.category;

import com.applegend.seokkeobom.ai.AiClient;
import com.applegend.seokkeobom.ai.AiException;
import com.applegend.seokkeobom.ai.WordContext;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.session.SessionEventPublisher;
import com.applegend.seokkeobom.session.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단어 트리 가지 뻗기(AI 단어 생성). 단어는 세션마다 따로 자라고, 뿌리(하위 카테고리)마다 최대 20개.
 * AI 호출은 오래 걸릴 수 있어 트랜잭션 밖에서 하고, 같은 뿌리의 중복 요청은 메모리 잠금으로 막는다(서버 1대 기준).
 */
@Service
public class WordTreeService {
    public static final int DEFAULT_EXPAND = 5;
    public static final int MAX_EXPAND = 10;
    private static final int MAX_WORD_LENGTH = 20;

    private final CatalogService catalog;
    private final WordNodeRepository wordNodes;
    private final SessionService sessions;
    private final SessionEventPublisher events;
    private final AiClient ai;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public WordTreeService(CatalogService catalog, WordNodeRepository wordNodes, SessionService sessions,
                           SessionEventPublisher events, AiClient ai) {
        this.catalog = catalog;
        this.wordNodes = wordNodes;
        this.sessions = sessions;
        this.events = events;
        this.ai = ai;
    }

    public record ExpandRequest(Long sessionId, Integer count) {}
    public record ExpandResponse(Long parentId, Long rootId, List<CatalogService.WordDto> words,
                                 int rootWordCount, int remaining, String aiMode) {}

    public CatalogService.WordTreeDto tree(Long categoryId, Long sessionId, Long userId) {
        if (sessionId != null) sessions.requireMember(sessionId, userId);
        return catalog.wordTree(categoryId, sessionId);
    }

    public ExpandResponse expand(Long nodeId, Long userId, ExpandRequest req) {
        if (req == null || req.sessionId() == null) {
            throw ApiException.badRequest("SESSION_REQUIRED", "세션 정보가 없습니다. 처음 화면에서 다시 시작해 주세요.");
        }
        Long sessionId = req.sessionId();
        WordNode node = catalog.requireNode(nodeId);
        if (node.getSessionId() != null && !node.getSessionId().equals(sessionId)) {
            throw ApiException.notFound("WORD_NOT_FOUND", "이 세션의 단어가 아닙니다. 트리를 새로고침해 주세요.");
        }
        sessions.requireActiveMemberAndTouch(sessionId, userId);
        Category category = catalog.requireCategory(node.getCategoryId());
        WordNode root = node.isRoot() ? node : catalog.requireNode(node.getRootId());

        String lockKey = sessionId + ":" + root.getId();
        if (!inFlight.add(lockKey)) {
            throw ApiException.conflict("ALREADY_EXPANDING", "'" + root.getText() + "'에서 단어를 만들고 있어요. 잠시만 기다려 주세요.");
        }
        try {
            int used = (int) wordNodes.countByRootIdAndSessionId(root.getId(), sessionId);
            int remaining = WordNode.MAX_WORDS_PER_ROOT - used;
            if (remaining <= 0) {
                throw ApiException.conflict("ROOT_FULL", "'" + root.getText() + "'에는 단어를 "
                        + WordNode.MAX_WORDS_PER_ROOT + "개까지만 뻗을 수 있어요. 다른 하위 카테고리에서 뻗어 보세요.");
            }
            int want = Math.min(clamp(req.count()), remaining);

            List<WordNode> sessionWords = wordNodes.findByCategoryIdAndSessionIdOrderByIdAsc(category.getId(), sessionId);
            Set<String> taken = new HashSet<>();
            sessionWords.forEach(w -> taken.add(normalize(w.getText())));
            wordNodes.findByCategoryIdAndSessionIdIsNullOrderByIdAsc(category.getId())
                    .forEach(r -> taken.add(normalize(r.getText())));

            WordContext ctx = new WordContext(category.getSlug(), category.getName(), root.getText(),
                    pathOf(node, sessionWords), sessionWords.stream().map(WordNode::getText).toList(), want);
            List<String> generated;
            try {
                generated = ai.generateWords(ctx);
            } catch (AiException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FAILED", e.getMessage());
            }

            List<WordNode> saved = new ArrayList<>();
            for (String raw : generated) {
                if (saved.size() >= want) break;
                String text = clean(raw);
                if (text.isEmpty() || !taken.add(normalize(text))) continue;
                saved.add(wordNodes.save(WordNode.aiWord(node, root.getId(), sessionId, text)));
            }
            if (saved.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FAILED",
                        "새로운 단어를 만들지 못했어요. 다시 시도하거나 다른 단어에서 뻗어 보세요.");
            }

            int total = used + saved.size();
            events.publish(sessionId, SessionEventPublisher.TREE_EXPANDED, Map.of(
                    "categoryId", category.getId(), "rootId", root.getId(), "parentId", node.getId(),
                    "words", saved.stream().map(WordNode::getText).toList(), "byUserId", userId));
            List<CatalogService.WordDto> dtos = saved.stream()
                    .map(n -> new CatalogService.WordDto(n.getId(), n.getText(), List.of()))
                    .toList();
            return new ExpandResponse(node.getId(), root.getId(), dtos, total,
                    WordNode.MAX_WORDS_PER_ROOT - total, ai.wordMode());
        } finally {
            inFlight.remove(lockKey);
        }
    }

    /** 뿌리 바로 아래부터 node까지의 단어 경로 (뿌리에서 뻗으면 빈 목록) */
    private static List<String> pathOf(WordNode node, List<WordNode> sessionWords) {
        Map<Long, WordNode> byId = new HashMap<>();
        sessionWords.forEach(w -> byId.put(w.getId(), w));
        LinkedList<String> path = new LinkedList<>();
        WordNode cur = node;
        while (cur != null && !cur.isRoot()) {
            path.addFirst(cur.getText());
            cur = byId.get(cur.getParentId());
        }
        return path;
    }

    private static int clamp(Integer count) {
        if (count == null) return DEFAULT_EXPAND;
        return Math.max(1, Math.min(MAX_EXPAND, count));
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String t = raw.strip().replaceAll("^[-•\\d.)\\s]+", "").replaceAll("[\"'`]", "").strip();
        return t.length() > MAX_WORD_LENGTH ? "" : t;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
