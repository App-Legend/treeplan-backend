package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.auth.Accounts;
import com.applegend.seokkeobom.ai.AiClient;
import com.applegend.seokkeobom.ai.AiException;
import com.applegend.seokkeobom.ai.GenerationContext;
import com.applegend.seokkeobom.auth.User;
import com.applegend.seokkeobom.auth.UserRepository;
import com.applegend.seokkeobom.category.CatalogService;
import com.applegend.seokkeobom.category.Category;
import com.applegend.seokkeobom.category.ResultTemplate;
import com.applegend.seokkeobom.category.TemplateItem;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.common.GenerationStatus;
import com.applegend.seokkeobom.draw.Draw;
import com.applegend.seokkeobom.draw.DrawService;
import com.applegend.seokkeobom.idea.Idea;
import com.applegend.seokkeobom.idea.IdeaService;
import com.applegend.seokkeobom.result.ResultDtos.*;
import com.applegend.seokkeobom.session.SessionEventPublisher;
import com.applegend.seokkeobom.session.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/** 결과물 생성·조회·수정·재생성·삭제 (화면 4~5, 7) */
@Service
public class ResultService {
    private static final Logger log = LoggerFactory.getLogger(ResultService.class);
    private static final int MAX_ITEM_LENGTH = 5000;
    private static final int MAX_FEEDBACK_LENGTH = 300;

    private final ResultRepository results;
    private final IdeaService ideaService;
    private final DrawService drawService;
    private final CatalogService catalog;
    private final SessionService sessions;
    private final SessionEventPublisher events;
    private final UserRepository users;
    private final AiClient ai;
    private final ObjectMapper objectMapper;

    public ResultService(ResultRepository results, IdeaService ideaService, DrawService drawService,
                         CatalogService catalog, SessionService sessions, SessionEventPublisher events,
                         UserRepository users, AiClient ai, ObjectMapper objectMapper) {
        this.results = results;
        this.ideaService = ideaService;
        this.drawService = drawService;
        this.catalog = catalog;
        this.sessions = sessions;
        this.events = events;
        this.users = users;
        this.ai = ai;
        this.objectMapper = objectMapper;
    }

    /** POST /ideas/{id}/result — 이미 채택된 아이디어면 기존 결과물을 돌려준다. */
    public ResultResponse create(Long ideaId, Long userId) {
        Idea idea = ideaService.require(ideaId);
        sessions.requireActiveMemberAndTouch(idea.getSessionId(), userId);
        if (idea.getStatus() != GenerationStatus.SUCCESS) {
            throw ApiException.conflict("IDEA_NOT_READY", "아이디어 생성이 끝난 뒤에 결과물을 만들 수 있습니다.");
        }
        Category category = catalog.requireCategory(idea.getCategoryId());
        ResultTemplate template = catalog.requireTemplate(category.getTemplateId());
        Draw draw = drawService.require(idea.getDrawId());

        Optional<Result> existing = results.findByIdeaId(ideaId);
        Result result;
        if (existing.isPresent() && !existing.get().isDeleted()) {
            Result current = existing.get();
            if (current.getStatus() == GenerationStatus.SUCCESS) {
                return toResponse(current, idea, userId, null);
            }
            // 실패했거나 오래 멈춘 실행안은 다시 만든다. 지금 만드는 중이면 중복 실행을 막는다.
            Instant now = Instant.now();
            if (results.markGenerating(current.getId(), GenerationStatus.GENERATING, now,
                    now.minus(IdeaService.STALE_GENERATION)) == 0) {
                throw ApiException.conflict("ALREADY_GENERATING", "실행안을 만들고 있어요. 잠시만 기다려 주세요.");
            }
            result = results.findById(current.getId()).orElseThrow();
        } else if (existing.isPresent()) {
            result = existing.get();
            result.revive(userId);
            result = results.save(result);
        } else {
            try {
                result = results.saveAndFlush(new Result(ideaId, idea.getSessionId(), userId, category.getId(),
                        category.getName(), template.getId(), idea.getTitle(), draw.getWords()));
            } catch (DataIntegrityViolationException e) {
                Result other = results.findByIdeaId(ideaId).orElseThrow(() -> e);
                return toResponse(other, idea, userId, null);
            }
        }

        result = runGeneration(result, idea, category, template, draw.getWords(), null, false).result();
        if (result.getStatus() == GenerationStatus.SUCCESS) {
            String author = users.findById(userId).map(User::getName).orElse("팀원");
            events.publish(result.getSessionId(), SessionEventPublisher.RESULT_CREATED, Map.of(
                    "resultId", result.getId(), "title", result.getTitle(), "authorId", userId, "authorName", author));
        }
        return toResponse(result, idea, userId, null);
    }

    public ResultResponse get(Long resultId, Long userId) {
        Result result = requireVisible(resultId, userId);
        return toResponse(result, ideaService.require(result.getIdeaId()), userId, null);
    }

    /** PATCH — 양식 항목 구조는 유지한 채 내용만 바꾼다. */
    public ResultResponse update(Long resultId, Long userId, Map<String, String> changes) {
        Result result = requireVisible(resultId, userId);
        sessions.requireActiveMemberAndTouch(result.getSessionId(), userId);
        if (result.getStatus() != GenerationStatus.SUCCESS) {
            throw ApiException.conflict("RESULT_NOT_READY", "생성이 끝난 결과물만 수정할 수 있습니다.");
        }
        if (changes == null || changes.isEmpty()) {
            throw ApiException.badRequest("NO_CHANGES", "수정할 항목이 없습니다.");
        }
        List<TemplateItem> items = catalog.items(catalog.requireTemplate(result.getTemplateId()));
        Set<String> allowed = new HashSet<>();
        items.forEach(i -> allowed.add(i.key()));
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (!allowed.contains(e.getKey())) {
                throw ApiException.badRequest("TEMPLATE_MISMATCH", "양식에 없는 항목은 추가할 수 없습니다.");
            }
            if (e.getValue() != null && e.getValue().length() > MAX_ITEM_LENGTH) {
                throw ApiException.badRequest("TOO_LONG", "한 항목은 5000자 이하로 입력해 주세요.");
            }
        }
        Map<String, String> body = readBody(result.getBodyJson());
        changes.forEach((k, v) -> body.put(k, v == null ? "" : v));
        result.edit(writeBody(orderBy(items, body)));
        result = results.save(result);
        return toResponse(result, ideaService.require(result.getIdeaId()), userId, null);
    }

    /** POST /results/{id}/regenerate — 세부안 보완. 실패해도 기존 본문은 보존한다. */
    public ResultResponse regenerate(Long resultId, Long userId, String feedback) {
        Result result = requireVisible(resultId, userId);
        sessions.requireActiveMemberAndTouch(result.getSessionId(), userId);
        if (feedback != null && feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw ApiException.badRequest("TOO_LONG", "보완 요청은 300자 이하로 적어 주세요.");
        }
        Instant now = Instant.now();
        int updated = results.markGenerating(result.getId(), GenerationStatus.GENERATING, now,
                now.minus(IdeaService.STALE_GENERATION));
        if (updated == 0) {
            throw ApiException.conflict("ALREADY_GENERATING", "이미 결과물을 다시 만들고 있어요. 잠시만 기다려 주세요.");
        }
        result = results.findById(resultId).orElseThrow();
        Idea idea = ideaService.require(result.getIdeaId());
        Category category = catalog.requireCategory(result.getCategoryId());
        ResultTemplate template = catalog.requireTemplate(result.getTemplateId());
        Outcome outcome = runGeneration(result, idea, category, template, result.getWords(), feedback, true);
        // 기존 본문이 있었는데 보완이 실패한 경우: 본문은 그대로 두고 안내 문구만 응답에 싣는다.
        String notice = (outcome.error() != null && outcome.result().getStatus() == GenerationStatus.SUCCESS)
                ? "보완하지 못해 기존 내용을 유지했어요. " + outcome.error()
                : null;
        return toResponse(outcome.result(), idea, userId, notice);
    }

    public DeleteResponse delete(Long resultId, Long userId) {
        Result result = requireVisible(resultId, userId);
        if (!sessions.isOwner(result.getSessionId(), userId)) {
            throw ApiException.forbidden("NOT_OWNER", "결과물 삭제는 세션 방장만 할 수 있습니다.");
        }
        result.softDelete();
        results.save(result);
        return new DeleteResponse(true);
    }

    /** 내보내기·조회 공통: 존재·삭제 여부와 같은 세션 참여자인지 */
    public Result requireVisible(Long resultId, Long userId) {
        Result result = results.findById(resultId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> ApiException.notFound("RESULT_NOT_FOUND", "결과물을 찾을 수 없습니다. 삭제되었을 수 있어요."));
        sessions.requireMember(result.getSessionId(), userId);
        return result;
    }

    public List<ResultItem> itemsOf(Result result) {
        List<TemplateItem> items = catalog.items(catalog.requireTemplate(result.getTemplateId()));
        Map<String, String> body = readBody(result.getBodyJson());
        return items.stream()
                .map(i -> new ResultItem(i.key(), i.label(), i.description(), body.getOrDefault(i.key(), "")))
                .toList();
    }

    // ---------- 내부 ----------

    private record Outcome(Result result, String error) {}

    private Outcome runGeneration(Result result, Idea idea, Category category, ResultTemplate template,
                                 List<String> words, String feedback, boolean isRegeneration) {
        List<TemplateItem> items = catalog.items(template);
        GenerationContext ctx = new GenerationContext(category.getSlug(), category.getName(), words,
                idea.getTitle(), idea.getSummary(), items, template.getAiInstruction(), feedback);
        String error = null;
        try {
            Map<String, String> body = ai.generateResult(ctx);
            result.succeed(writeBody(orderBy(items, body)));
        } catch (AiException e) {
            error = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Result generation failed unexpectedly", e);
            error = "결과물을 만드는 중 문제가 생겼습니다. 다시 시도해 주세요.";
        }
        if (error != null) {
            if (isRegeneration) result.restoreAfterFailedRegeneration(error);
            else result.fail(error);
        }
        return new Outcome(results.save(result), error);
    }

    private ResultResponse toResponse(Result r, Idea idea, Long userId, String notice) {
        User author = users.findById(r.getAuthorId()).orElse(null);
        String authorName = author == null ? "알 수 없음" : author.getName();
        boolean saved = author != null && !author.isGuest();
        return new ResultResponse(r.getId(), r.getIdeaId(), r.getSessionId(), r.getTitle(), idea.getSummary(),
                r.getCategoryId(), r.getCategoryName(), r.getWords(), itemsOf(r), r.getStatus(), r.getFailureReason(),
                r.isEdited(), r.getCreatedAt(), r.getUpdatedAt(), authorName, sessions.isOwner(r.getSessionId(), userId),
                notice, ai.mode(), saved, saved ? null : Accounts.LOGIN_PROMPT);
    }

    private static Map<String, String> orderBy(List<TemplateItem> items, Map<String, String> body) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (TemplateItem i : items) ordered.put(i.key(), body.getOrDefault(i.key(), ""));
        return ordered;
    }

    private Map<String, String> readBody(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BODY_CORRUPTED", "결과물 내용을 읽지 못했습니다.");
        }
    }

    private String writeBody(Map<String, String> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
