package com.applegend.seokkeobom.category;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    /** food / program / contest 처럼 코드에서 쓰는 식별자 */
    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int sortOrder;

    protected Category() {}

    public Category(String name, String slug, Long templateId, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.templateId = templateId;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Long getTemplateId() { return templateId; }
    public int getSortOrder() { return sortOrder; }
}
