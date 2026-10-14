package com.applegend.seokkeobom.idea;

import com.applegend.seokkeobom.common.GenerationStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ideas", indexes = @Index(name = "idx_ideas_session", columnList = "sessionId"))
public class Idea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 추출 1 : 아이디어 1 */
    @Column(nullable = false, unique = true)
    private Long drawId;

    /** 히스토리 조회를 빠르게 하려고 함께 저장 */
    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    /** 히스토리 카테고리 필터용 */
    @Column(nullable = false)
    private Long categoryId;

    @Column(length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationStatus status;

    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false)
    private boolean bookmarked;

    private Instant bookmarkedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Idea() {}

    public Idea(Long drawId, Long sessionId, Long userId, Long categoryId) {
        this.drawId = drawId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.categoryId = categoryId;
        this.status = GenerationStatus.GENERATING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void startGenerating() {
        this.status = GenerationStatus.GENERATING;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void succeed(String title, String summary) {
        this.title = title;
        this.summary = summary;
        this.status = GenerationStatus.SUCCESS;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = GenerationStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
        this.bookmarkedAt = bookmarked ? Instant.now() : null;
    }

    public Long getId() { return id; }
    public Long getDrawId() { return drawId; }
    public Long getSessionId() { return sessionId; }
    public Long getUserId() { return userId; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public GenerationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public boolean isBookmarked() { return bookmarked; }
    public Instant getBookmarkedAt() { return bookmarkedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
