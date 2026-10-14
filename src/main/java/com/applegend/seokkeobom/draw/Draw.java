package com.applegend.seokkeobom.draw;

import com.applegend.seokkeobom.common.StringListConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 단어 추출 한 번(화살표 ④). 아이디어 생성은 이 추출 ID로 따로 요청한다(⑤). */
@Entity
@Table(name = "draws", indexes = @Index(name = "idx_draws_session", columnList = "sessionId"))
public class Draw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    /** 명세는 '선택 카테고리 ID 목록'이지만, 요구사항(다른 분야와 섞지 않음)에 따라 MVP는 1개만 받는다. */
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 200)
    private List<String> categoryIds = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 500)
    private List<String> fixedWords = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> excludedWords = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 500)
    private List<String> words = new ArrayList<>();

    @Column(nullable = false)
    private int attemptNo;

    @Column(nullable = false)
    private Instant createdAt;

    protected Draw() {}

    public Draw(Long sessionId, Long userId, Long categoryId, List<String> fixedWords, List<String> excludedWords,
                List<String> words, int attemptNo) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.categoryIds = new ArrayList<>(List.of(String.valueOf(categoryId)));
        this.fixedWords = new ArrayList<>(fixedWords);
        this.excludedWords = new ArrayList<>(excludedWords);
        this.words = new ArrayList<>(words);
        this.attemptNo = attemptNo;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Long getUserId() { return userId; }
    public Long getCategoryId() { return Long.valueOf(categoryIds.get(0)); }
    public List<String> getFixedWords() { return fixedWords; }
    public List<String> getExcludedWords() { return excludedWords; }
    public List<String> getWords() { return words; }
    public int getAttemptNo() { return attemptNo; }
    public Instant getCreatedAt() { return createdAt; }
}
