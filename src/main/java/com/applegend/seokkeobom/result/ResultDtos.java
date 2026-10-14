package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.common.GenerationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ResultDtos {
    private ResultDtos() {}

    public record ResultItem(String key, String label, String description, String content) {}

    public record ResultResponse(Long resultId, Long ideaId, Long sessionId, String title, String ideaSummary,
                                 Long categoryId, String categoryName, List<String> words, List<ResultItem> items,
                                 GenerationStatus status, String failureReason, boolean edited,
                                 Instant createdAt, Instant updatedAt, String authorName, boolean canDelete,
                                 String notice, String aiMode) {}

    public record UpdateRequest(Map<String, String> items) {}
    public record RegenerateRequest(String feedback) {}
    public record DeleteResponse(boolean success) {}
}
