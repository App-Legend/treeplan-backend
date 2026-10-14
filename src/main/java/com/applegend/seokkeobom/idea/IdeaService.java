package com.applegend.seokkeobom.idea;

import com.applegend.seokkeobom.ai.AiClient;
import com.applegend.seokkeobom.ai.AiException;
import com.applegend.seokkeobom.ai.GenerationContext;
import com.applegend.seokkeobom.ai.IdeaDraft;
import com.applegend.seokkeobom.category.CatalogService;
import com.applegend.seokkeobom.category.Category;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.common.GenerationStatus;
import com.applegend.seokkeobom.draw.Draw;
import com.applegend.seokkeobom.draw.DrawService;
import com.applegend.seokkeobom.idea.IdeaDtos.*;
import com.applegend.seokkeobom.result.Result;
import com.applegend.seokkeobom.result.ResultRepository;
import com.applegend.seokkeobom.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 아이디어 생성(화살표 ⑤ → ⑥ 가).
 * AI 호출은 오래 걸리므로 트랜잭션 밖에서 하고, '생성 중' 표시는 조건부 갱신으로 먼저 확정한다.
 */
@Service
public class IdeaService {
    private static final Logger log = LoggerFactory.getLogger(IdeaService.class);
    /** 서버가 중간에 꺼져 '생성 중'으로 남은 경우, 이 시간이 지나면 다시 시도를 허용한다. */
    public static final Duration STALE_GENERATION = Duration.ofSeconds(120);

    private final IdeaRepository ideas;
    private final ResultRepository results;
    private final DrawService drawService;
    private final CatalogService catalog;
    private final SessionService sessions;
    private final AiClient ai;

    public IdeaService(IdeaRepository ideas, ResultRepository results, DrawService drawService,
                       CatalogService catalog, SessionService sessions, AiClient ai) {
        this.ideas = ideas;
        this.results = results;
        this.drawService = drawService;
        this.catalog = catalog;
        this.sessions = sessions;
        this.ai = ai;
    }

    public IdeaResponse generate(Long drawId, Long userId) {
        Draw draw = drawService.require(drawId);
        sessions.requireActiveMemberAndTouch(draw.getSessionId(), userId);
        Category category = catalog.requireCategory(draw.getCategoryId());

        Idea idea = ideas.findByDrawId(drawId).orElse(null);
        Instant now = Instant.now();
        if (idea == null) {
            try {
                idea = ideas.saveAndFlush(new Idea(drawId, draw.getSessionId(), userId, draw.getCategoryId()));
            } catch (DataIntegrityViolationException e) {
                throw alreadyGenerating();
            }
        } else {
            boolean adopted = results.findByIdeaId(idea.getId()).filter(r -> !r.isDeleted()).isPresent();
            if (adopted) {
                throw ApiException.conflict("ALREADY_ADOPTED", "이미 결과물로 만든 아이디어입니다. 새 조합으로 시도해 주세요.");
            }
            int updated = ideas.markGenerating(idea.getId(), GenerationStatus.GENERATING, now, now.minus(STALE_GENERATION));
            if (updated == 0) throw alreadyGenerating();
            idea = ideas.findById(idea.getId()).orElseThrow();
        }

        GenerationContext ctx = new GenerationContext(category.getSlug(), category.getName(), draw.getWords(),
                null, null, List.of(), "", null);
        try {
            IdeaDraft draft = ai.generateIdea(ctx);
            idea.succeed(draft.title(), draft.summary());
        } catch (AiException e) {
            idea.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Idea generation failed unexpectedly", e);
            idea.fail("아이디어를 만드는 중 문제가 생겼습니다. 같은 조합으로 다시 시도해 주세요.");
        }
        idea = ideas.save(idea);
        return toResponse(idea, draw, category);
    }

    public BookmarkResponse bookmark(Long ideaId, Long userId, boolean bookmarked) {
        Idea idea = require(ideaId);
        sessions.requireActiveMemberAndTouch(idea.getSessionId(), userId);
        if (idea.getStatus() != GenerationStatus.SUCCESS) {
            throw ApiException.conflict("IDEA_NOT_READY", "생성이 끝난 아이디어만 보관할 수 있습니다.");
        }
        idea.setBookmarked(bookmarked);
        idea = ideas.save(idea);
        return new BookmarkResponse(idea.getId(), idea.isBookmarked(), idea.getBookmarkedAt());
    }

    public Idea require(Long ideaId) {
        return ideas.findById(ideaId)
                .orElseThrow(() -> ApiException.notFound("IDEA_NOT_FOUND", "아이디어를 찾을 수 없습니다."));
    }

    private IdeaResponse toResponse(Idea idea, Draw draw, Category category) {
        Long resultId = results.findByIdeaId(idea.getId()).filter(r -> !r.isDeleted()).map(Result::getId).orElse(null);
        return new IdeaResponse(idea.getId(), draw.getId(), idea.getSessionId(), category.getId(), category.getName(),
                draw.getWords(), idea.getTitle(), idea.getSummary(), idea.getStatus(), idea.getFailureReason(),
                idea.isBookmarked(), resultId, ai.mode());
    }

    private static ApiException alreadyGenerating() {
        return ApiException.conflict("ALREADY_GENERATING", "이 조합으로 이미 아이디어를 만들고 있어요. 잠시만 기다려 주세요.");
    }
}
