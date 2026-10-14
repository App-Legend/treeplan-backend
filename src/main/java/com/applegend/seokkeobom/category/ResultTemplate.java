package com.applegend.seokkeobom.category;

import jakarta.persistence.*;

/** 카테고리별 결과물 양식(절차 템플릿). 설계자가 관리하고 사용자 화면에서는 읽기만 한다. */
@Entity
@Table(name = "result_templates")
public class ResultTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [{"key":"concept","label":"콘셉트","description":"..."}, ...] 순서대로 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String aiInstruction;

    protected ResultTemplate() {}

    public ResultTemplate(String itemsJson, String aiInstruction) {
        this.itemsJson = itemsJson;
        this.aiInstruction = aiInstruction;
    }

    public Long getId() { return id; }
    public String getItemsJson() { return itemsJson; }
    public String getAiInstruction() { return aiInstruction; }
}
