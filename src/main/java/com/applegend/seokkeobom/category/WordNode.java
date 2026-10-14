package com.applegend.seokkeobom.category;

import jakarta.persistence.*;

/** 단어 트리의 한 칸. 깊이 1 = 분류(재료·조리법…), 깊이 2 = 실제로 뽑히는 단어. */
@Entity
@Table(name = "word_nodes", indexes = @Index(name = "idx_word_nodes_category", columnList = "categoryId"))
public class WordNode {
    public static final int GROUP_DEPTH = 1;
    public static final int WORD_DEPTH = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    private Long parentId;

    @Column(nullable = false, length = 40)
    private String text;

    @Column(nullable = false)
    private int depth;

    protected WordNode() {}

    public WordNode(Long categoryId, Long parentId, String text, int depth) {
        this.categoryId = categoryId;
        this.parentId = parentId;
        this.text = text;
        this.depth = depth;
    }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public Long getParentId() { return parentId; }
    public String getText() { return text; }
    public int getDepth() { return depth; }
}
