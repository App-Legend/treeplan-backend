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

    /** 로그인 없이 작업하는 임시 사용자. 히스토리·팀 코드는 쓸 수 없고, 로그인하면 작업이 계정으로 옮겨진다. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean guest;

    protected User() {}

    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public static User guest(String email, String passwordHash) {
        User u = new User(email, passwordHash, "게스트");
        u.guest = true;
        return u;
    }

    public Long getId() { return id; }
    public boolean isGuest() { return guest; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
