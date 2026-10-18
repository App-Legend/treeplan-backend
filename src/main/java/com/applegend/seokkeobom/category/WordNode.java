package com.applegend.seokkeobom.category;

import jakarta.persistence.*;

/**
 * 단어 트리의 한 칸.
 * 깊이 1 = 하위 카테고리(뿌리, 팀이 정한 고정 3개, 세션 공용).
 * 깊이 2 이상 = AI가 뻗은 단어. 세션마다 따로 자라며, 뿌리마다 최대 {@link #MAX_WORDS_PER_ROOT}개.
 */
@Entity
@Table(name = "word_nodes", indexes = {
        @Index(name = "idx_word_nodes_category", columnList = "categoryId"),
        @Index(name = "idx_word_nodes_session_root", columnList = "sessionId,rootId")})
public class WordNode {
    public static final int ROOT_DEPTH = 1;
    public static final int MAX_WORDS_PER_ROOT = 20;
    public static final String SOURCE_SEED = "SEED";
    public static final String SOURCE_AI = "AI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    private Long parentId;

    /** 이 단어가 속한 뿌리(하위 카테고리) ID. 뿌리 자신은 null */
    private Long rootId;

    /** 단어가 자란 세션. 뿌리(세션 공용)는 null */
    private Long sessionId;

    @Column(nullable = false, length = 40)
    private String text;

    @Column(nullable = false)
    private int depth;

    /** SEED(팀 지정) 또는 AI */
    @Column(length = 8)
    private String source;

    protected WordNode() {}

    /** 뿌리(하위 카테고리) */
    public static WordNode root(Long categoryId, String text) {
        WordNode n = new WordNode();
        n.categoryId = categoryId;
        n.text = text;
        n.depth = ROOT_DEPTH;
        n.source = SOURCE_SEED;
        return n;
    }

    /** AI가 parent 아래에 뻗은 단어 */
    public static WordNode aiWord(WordNode parent, Long rootId, Long sessionId, String text) {
        WordNode n = new WordNode();
        n.categoryId = parent.getCategoryId();
        n.parentId = parent.getId();
        n.rootId = rootId;
        n.sessionId = sessionId;
        n.text = text;
        n.depth = parent.getDepth() + 1;
        n.source = SOURCE_AI;
        return n;
    }

    public boolean isRoot() { return depth == ROOT_DEPTH; }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public Long getParentId() { return parentId; }
    public Long getRootId() { return rootId; }
    public Long getSessionId() { return sessionId; }
    public String getText() { return text; }
    public int getDepth() { return depth; }
    public String getSource() { return source; }
}
