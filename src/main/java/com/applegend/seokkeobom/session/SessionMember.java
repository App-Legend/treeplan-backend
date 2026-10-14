package com.applegend.seokkeobom.session;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "session_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_member", columnNames = {"sessionId", "userId"}))
public class SessionMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberRole role;

    @Column(nullable = false)
    private Instant joinedAt;

    protected SessionMember() {}

    public SessionMember(Long sessionId, Long userId, MemberRole role) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Long getUserId() { return userId; }
    public MemberRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
