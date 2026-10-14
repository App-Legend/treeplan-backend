package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.common.GenerationStatus;
import com.applegend.seokkeobom.common.StringListConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 카테고리 양식에 맞춘 결과물. 같은 세션 참여자 모두가 열람한다(별도 공유 덩어리 없음). */
@Entity
@Table(name = "results", indexes = {
        @Index(name = "idx_results_session", columnList = "sessionId"),
        @Index(name = "idx_results_created", columnList = "createdAt")})
public class Result {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 아이디어 1 : 결과물 0..1 */
    @Column(nullable = false, unique = true)
    private Long ideaId;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Long categoryId;

    /** 조회를 빠르게 하려고 복사해 둔 값 (6주차 문서의 선택 사항 중 '복사' 쪽을 택함) */
    @Column(nullable = false, length = 40)
    private String categoryName;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false, length = 120)
    private String title;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 500)
    private List<String> words = new ArrayList<>();

    /** 검색용 평문 ("유자 바삭함 산책") */
    @Column(nullable = false, length = 200)
    private String wordsText;

    /** {"concept":"...", "ingredients":"..."} 양식 항목별 본문 */
    @Column(columnDefinition = "TEXT")
    private String bodyJson;

    @Column(nullable = false)
    private boolean edited;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationStatus status;

    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    protected Result() {}

    public Result(Long ideaId, Long sessionId, Long authorId, Long categoryId, String categoryName, Long templateId,
                  String title, List<String> words) {
        this.ideaId = ideaId;
        this.sessionId = sessionId;
        this.authorId = authorId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.templateId = templateId;
        this.title = title;
        this.words = new ArrayList<>(words);
        this.wordsText = String.join(" ", words);
        this.status = GenerationStatus.GENERATING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void succeed(String bodyJson) {
        this.bodyJson = bodyJson;
        this.status = GenerationStatus.SUCCESS;
        this.failureReason = null;
        this.edited = false;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = GenerationStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    /** 보완 생성이 실패해도 기존 본문은 그대로 두고 상태만 되돌린다. */
    public void restoreAfterFailedRegeneration(String reason) {
        this.status = this.bodyJson == null ? GenerationStatus.FAILED : GenerationStatus.SUCCESS;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void edit(String bodyJson) {
        this.bodyJson = bodyJson;
        this.edited = true;
        this.updatedAt = Instant.now();
    }

    public void revive(Long authorId) {
        this.deleted = false;
        this.authorId = authorId;
        this.status = GenerationStatus.GENERATING;
        this.bodyJson = null;
        this.edited = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void softDelete() {
        this.deleted = true;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getIdeaId() { return ideaId; }
    public Long getSessionId() { return sessionId; }
    public Long getAuthorId() { return authorId; }
    public Long getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public Long getTemplateId() { return templateId; }
    public String getTitle() { return title; }
    public List<String> getWords() { return words; }
    public String getBodyJson() { return bodyJson; }
    public boolean isEdited() { return edited; }
    public GenerationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isDeleted() { return deleted; }
}
