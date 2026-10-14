package com.applegend.seokkeobom.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    /** 연속 로그인 실패 횟수 (명세: 서버가 확인할 것 - 연속 실패 횟수) */
    @Column(nullable = false)
    private int failedLoginCount;

    private Instant lockedUntil;

    protected User() {}

    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
