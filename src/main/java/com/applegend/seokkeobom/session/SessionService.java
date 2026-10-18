package com.applegend.seokkeobom.session;

import com.applegend.seokkeobom.auth.Accounts;
import com.applegend.seokkeobom.auth.User;
import com.applegend.seokkeobom.auth.UserRepository;
import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.session.SessionDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionService {
    /** 헷갈리는 글자(0/O, 1/I/L)를 뺀 팀 코드 문자 */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final IdeaSessionRepository sessions;
    private final SessionMemberRepository members;
    private final UserRepository users;
    private final SessionEventPublisher events;
    private final Accounts accounts;
    private final Duration inactiveLimit;
    private final SecureRandom random = new SecureRandom();

    public SessionService(IdeaSessionRepository sessions, SessionMemberRepository members, UserRepository users,
                          SessionEventPublisher events, Accounts accounts,
                          @Value("${app.session.inactive-days}") long inactiveDays) {
        this.sessions = sessions;
        this.members = members;
        this.users = users;
        this.events = events;
        this.accounts = accounts;
        this.inactiveLimit = Duration.ofDays(inactiveDays);
    }

    // ---------- API ----------

    @Transactional
    public CreateSessionResponse create(Long userId, String rawName, String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) {
            throw ApiException.badRequest("BAD_IDEMPOTENCY_KEY", "요청 식별자가 너무 깁니다.");
        }
        if (key != null) {
            Optional<IdeaSession> existing = sessions.findByOwnerIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) {
                IdeaSession s = existing.get();
                return new CreateSessionResponse(s.getId(), s.getName(), teamCodeFor(s, userId));
            }
        }
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) name = "새 발상 세션";
        if (name.length() > 40) {
            throw ApiException.badRequest("INVALID_NAME", "세션 이름은 40자 이하로 입력해 주세요.");
        }
        IdeaSession session = sessions.save(new IdeaSession(name, newTeamCode(), userId, key));
        members.save(new SessionMember(session.getId(), userId, MemberRole.OWNER));
        return new CreateSessionResponse(session.getId(), session.getName(), teamCodeFor(session, userId));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public JoinResponse join(Long userId, String rawCode) {
        accounts.requireRegistered(userId, "팀 코드로 함께 작업하려면 먼저 로그인해 주세요.");
        String code = rawCode.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        IdeaSession session = sessions.findByTeamCode(code)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "팀 코드를 찾을 수 없습니다. 코드를 다시 확인해 주세요."));
        expireIfStale(session);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw ApiException.conflict("CLOSED", "방장이 종료한 세션입니다. 새 세션을 시작하거나 새 팀 코드를 받아 주세요.");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, "EXPIRED", "30일 동안 활동이 없어 만료된 팀 코드입니다. 새 세션을 시작해 주세요.");
        }
        if (!members.existsBySessionIdAndUserId(session.getId(), userId)) {
            members.save(new SessionMember(session.getId(), userId, MemberRole.MEMBER));
            session.touch();
            sessions.save(session);
            String name = users.findById(userId).map(User::getName).orElse("팀원");
            events.publish(session.getId(), SessionEventPublisher.MEMBER_JOINED, Map.of("userId", userId, "name", name));
        }
        return new JoinResponse(session.getId(), session.getName());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionInfo info(Long sessionId, Long userId) {
        IdeaSession session = requireMember(sessionId, userId);
        expireIfStale(session);
        List<SessionMember> list = members.findBySessionIdOrderByJoinedAtAsc(sessionId);
        Map<Long, String> names = users.findAllById(list.stream().map(SessionMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        List<MemberDto> dtos = list.stream()
                .map(m -> new MemberDto(m.getUserId(), names.getOrDefault(m.getUserId(), "알 수 없음"), m.getRole(), m.getJoinedAt()))
                .toList();
        MemberRole myRole = list.stream().filter(m -> m.getUserId().equals(userId))
                .map(SessionMember::getRole).findFirst().orElse(MemberRole.MEMBER);
        return new SessionInfo(session.getId(), session.getName(), teamCodeFor(session, userId), session.getStatus(),
                dtos, myRole, session.getLastActivityAt());
    }

    @Transactional
    public LeaveResponse leave(Long sessionId, Long userId) {
        requireMember(sessionId, userId);
        SessionMember me = members.findBySessionIdAndUserId(sessionId, userId).orElseThrow();
        if (me.getRole() == MemberRole.OWNER) {
            throw ApiException.conflict("OWNER_CANNOT_LEAVE", "방장은 나갈 수 없습니다. 세션을 끝내려면 '세션 종료'를 눌러 주세요.");
        }
        members.delete(me);
        String name = users.findById(userId).map(User::getName).orElse("팀원");
        events.publish(sessionId, SessionEventPublisher.MEMBER_LEFT, Map.of("userId", userId, "name", name));
        return new LeaveResponse(true);
    }

    @Transactional
    public CloseResponse close(Long sessionId, Long userId) {
        IdeaSession session = requireMember(sessionId, userId);
        if (!session.getOwnerId().equals(userId)) {
            throw ApiException.forbidden("NOT_OWNER", "세션 종료는 방장만 할 수 있습니다.");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw ApiException.conflict("ALREADY_CLOSED", "이미 종료되었거나 만료된 세션입니다.");
        }
        session.close();
        sessions.save(session);
        events.publish(sessionId, SessionEventPublisher.SESSION_CLOSED, Map.of("closedAt", session.getClosedAt().toString()));
        return new CloseResponse(session.getClosedAt());
    }

    // ---------- 다른 서비스가 쓰는 공통 확인 ----------

    /** 읽기용: 세션 존재 + 요청자가 참여자인지. (종료된 세션의 결과물도 히스토리에서 열 수 있어야 하므로 진행 여부는 보지 않음) */
    public IdeaSession requireMember(Long sessionId, Long userId) {
        IdeaSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."));
        if (!members.existsBySessionIdAndUserId(sessionId, userId)) {
            throw ApiException.forbidden("NOT_MEMBER", "이 세션의 참여자만 볼 수 있습니다.");
        }
        return session;
    }

    /** 쓰기용: 참여자 + 세션이 진행 중인지. 통과하면 마지막 활동 일시를 갱신한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public IdeaSession requireActiveMemberAndTouch(Long sessionId, Long userId) {
        IdeaSession session = requireMember(sessionId, userId);
        expireIfStale(session);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw ApiException.conflict("SESSION_CLOSED", "방장이 종료한 세션이라 더 이상 만들 수 없습니다.");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, "SESSION_EXPIRED", "오래 활동이 없어 만료된 세션입니다. 새 세션을 시작해 주세요.");
        }
        session.touch();
        return sessions.save(session);
    }

    public boolean isOwner(Long sessionId, Long userId) {
        return members.findBySessionIdAndUserId(sessionId, userId)
                .map(m -> m.getRole() == MemberRole.OWNER).orElse(false);
    }

    public boolean isMember(Long sessionId, Long userId) {
        return members.existsBySessionIdAndUserId(sessionId, userId);
    }

    public List<Long> sessionIdsOf(Long userId) {
        return members.findSessionIdsByUserId(userId);
    }

    public Map<Long, IdeaSession> findAll(Collection<Long> ids) {
        return sessions.findAllById(ids).stream().collect(Collectors.toMap(IdeaSession::getId, Function.identity()));
    }

    public long memberCount(Long sessionId) {
        return members.countBySessionId(sessionId);
    }

    /** 협업(팀 코드)은 로그인이 필요하므로 게스트에게는 팀 코드를 주지 않는다. 로그인하면 같은 세션의 코드가 보인다. */
    private String teamCodeFor(IdeaSession session, Long userId) {
        return accounts.isGuest(userId) ? null : session.getTeamCode();
    }

    /** 예약 작업이 아직 돌지 않았어도 30일 무활동이면 즉시 만료 처리 */
    private void expireIfStale(IdeaSession session) {
        if (session.getStatus() == SessionStatus.ACTIVE
                && session.getLastActivityAt().isBefore(Instant.now().minus(inactiveLimit))) {
            session.expire();
            sessions.save(session);
        }
    }

    private String newTeamCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!sessions.existsByTeamCode(code)) return code;
        }
        throw new IllegalStateException("팀 코드를 만들지 못했습니다.");
    }
}
