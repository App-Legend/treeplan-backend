package com.applegend.seokkeobom.session;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class SessionDtos {
    private SessionDtos() {}

    public record CreateSessionRequest(String name) {}
    public record CreateSessionResponse(Long sessionId, String name, String teamCode) {}
    public record JoinRequest(@NotBlank(message = "팀 코드를 입력해 주세요.") String teamCode) {}
    public record JoinResponse(Long sessionId, String name) {}
    public record MemberDto(Long userId, String name, MemberRole role, Instant joinedAt) {}
    public record SessionInfo(Long sessionId, String name, String teamCode, SessionStatus status,
                              List<MemberDto> members, MemberRole myRole, Instant lastActivityAt) {}
    public record CloseResponse(Instant closedAt) {}
    public record LeaveResponse(boolean success) {}
}
