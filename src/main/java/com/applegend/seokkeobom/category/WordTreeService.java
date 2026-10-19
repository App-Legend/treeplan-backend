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
 * 단어 트리 가지 뻗기(AI 단어 생성). 단어는 세션마다 따로 자라고, 뿌리(하위 카테고리)마다 가지 뻗기는 최대 20번.
 * AI 호출은 오래 걸릴 수 있어 트랜잭션 밖에서 하고, 같은 뿌리의 중복 요청은 메모리 잠금으로 막는다(서버 1대 기준).
 */
@Service
public class WordTreeService {
    public static final int DEFAULT_EXPAND = 3;
    public static final int MAX_EXPAND = 10;
    private static final int MAX_WORD_LENGTH = 20;
    /** AI에게 필요한 수의 몇 배를 후보로 받아 서버가 무작위로 고른다 (AI가 매번 같은 단어로 몰리는 것 방지) */
    private static final int CANDIDATE_FACTOR = 4;
    private static final int MAX_CANDIDATES = 12;
    /** 매 요청마다 2개를 무작위로 골라 프롬프트에 넣는 연상 관점 */
    static final List<String> ANGLES = List.of("계절·날씨", "지역·장소", "전통·문화", "최신 유행", "사람·관계",
            "기술·도구", "일상 습관", "역사", "놀이·취미", "건강", "환경·자연", "직업", "해외", "세대");

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
                                 int rootWordCount, int expandCount, int remaining, String aiMode) {}

    public record ResetResponse(Long categoryId, int deleted) {}

    /** 트리 초기화: 이 세션에서 이 분야에 뻗은 단어를 모두 지워 뻗기 횟수도 0으로 돌린다. 팀원에게 알린다. */
    public ResetResponse reset(Long sessionId, Long categoryId, Long userId) {
        sessions.requireActiveMemberAndTouch(sessionId, userId);
        catalog.requireCategory(categoryId);
        // 단어를 만드는 중에 지우면 그 요청이 끝난 뒤 단어가 다시 생기므로 막는다
        if (inFlight.stream().anyMatch(k -> k.startsWith(sessionId + ":"))) {
            throw ApiException.conflict("ALREADY_EXPANDING", "단어를 만들고 있어요. 끝난 뒤 다시 초기화해 주세요.");
        }
        int deleted = wordNodes.deleteSessionWords(categoryId, sessionId);
        events.publish(sessionId, SessionEventPublisher.TREE_RESET, Map.of("categoryId", categoryId, "byUserId", userId));
        return new ResetResponse(categoryId, deleted);
    }

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
            int expands = wordNodes.countExpands(root.getId(), sessionId);
            if (expands >= WordNode.MAX_EXPANDS_PER_ROOT) {
                throw ApiException.conflict("ROOT_FULL", "'" + root.getText() + "'에서는 가지를 "
                        + WordNode.MAX_EXPANDS_PER_ROOT + "번까지만 뻗을 수 있어요. 다른 하위 카테고리에서 뻗어 보세요.");
            }
            int expandNo = expands + 1;
            int want = clamp(req.count());

            List<WordNode> sessionWords = wordNodes.findByCategoryIdAndSessionIdOrderByIdAsc(category.getId(), sessionId);
            Set<String> taken = new HashSet<>();
            sessionWords.forEach(w -> taken.add(normalize(w.getText())));
            wordNodes.findByCategoryIdAndSessionIdIsNullOrderByIdAsc(category.getId())
                    .forEach(r -> taken.add(normalize(r.getText())));

            List<String> angles = new ArrayList<>(ANGLES);
            Collections.shuffle(angles);
            WordContext ctx = new WordContext(category.getSlug(), category.getName(), root.getText(),
                    pathOf(node, sessionWords), sessionWords.stream().map(WordNode::getText).toList(),
                    Math.min(MAX_CANDIDATES, want * CANDIDATE_FACTOR), angles.subList(0, 2));
            List<String> generated;
            try {
                generated = ai.generateWords(ctx);
            } catch (AiException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FAILED", e.getMessage());
            }

            // 후보 중에서 무작위로 고른다
            generated = new ArrayList<>(generated);
            Collections.shuffle(generated);
            List<WordNode> saved = new ArrayList<>();
            for (String raw : generated) {
                if (saved.size() >= want) break;
                String text = clean(raw);
                if (text.isEmpty() || !taken.add(normalize(text))) continue;
                saved.add(wordNodes.save(WordNode.aiWord(node, root.getId(), sessionId, text, expandNo)));
            }
            if (saved.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FAILED",
                        "새로운 단어를 만들지 못했어요. 다시 시도하거나 다른 단어에서 뻗어 보세요.");
            }

            int rootWords = (int) wordNodes.countByRootIdAndSessionId(root.getId(), sessionId);
            events.publish(sessionId, SessionEventPublisher.TREE_EXPANDED, Map.of(
                    "categoryId", category.getId(), "rootId", root.getId(), "parentId", node.getId(),
                    "words", saved.stream().map(WordNode::getText).toList(), "byUserId", userId));
            List<CatalogService.WordDto> dtos = saved.stream()
                    .map(n -> new CatalogService.WordDto(n.getId(), n.getText(), List.of()))
                    .toList();
            return new ExpandResponse(node.getId(), root.getId(), dtos, rootWords, expandNo,
                    WordNode.MAX_EXPANDS_PER_ROOT - expandNo, ai.wordMode());
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
        // 목록 표시("1. ", "2) ", "- ", "• ")만 지운다. "3D 프린팅"처럼 숫자로 시작하는 단어는 그대로 둔다.
        String t = raw.strip().replaceAll("^(\\d+[.)]\\s*|[-•]\\s*)", "").replaceAll("[\"'`]", "").strip();
        return t.length() > MAX_WORD_LENGTH ? "" : t;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
