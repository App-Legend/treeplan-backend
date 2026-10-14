package com.applegend.seokkeobom.idea;

import com.applegend.seokkeobom.common.GenerationStatus;

import java.time.Instant;
import java.util.List;

public final class IdeaDtos {
    private IdeaDtos() {}

    public record IdeaResponse(Long ideaId, Long drawId, Long sessionId, Long categoryId, String categoryName,
                               List<String> words, String title, String summary, GenerationStatus status,
                               String failureReason, boolean bookmarked, Long resultId, String aiMode) {}

    public record BookmarkRequest(Boolean bookmarked) {}
    public record BookmarkResponse(Long ideaId, boolean bookmarked, Instant bookmarkedAt) {}
}
