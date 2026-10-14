package com.applegend.seokkeobom.session;

import jakarta.persistence.*;
import java.time.Instant;

/** 발상 세션. (이름 충돌을 피하려고 클래스명은 IdeaSession, 테이블명은 sessions) */
@Entity
@Table(name = "sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sessions_owner_idem", columnNames = {"ownerId", "idempotencyKey"}))
public class IdeaSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, unique = true, length = 12)
    private String teamCode;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    @Column(nullable = false)
    private Instant lastActivityAt;

    private Instant closedAt;

    @Column(nullable = false)
    private Instant createdAt;

    /** 중복 클릭으로 인한 이중 생성을 막는 멱등 키 */
    @Column(length = 64)
    private String idempotencyKey;

    protected IdeaSession() {}

    public IdeaSession(String name, String teamCode, Long ownerId, String idempotencyKey) {
        this.name = name;
        this.teamCode = teamCode;
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.lastActivityAt = this.createdAt;
    }

    public void touch() { this.lastActivityAt = Instant.now(); }

    public void close() {
        this.status = SessionStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public void expire() { this.status = SessionStatus.EXPIRED; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTeamCode() { return teamCode; }
    public Long getOwnerId() { return ownerId; }
    public SessionStatus getStatus() { return status; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
