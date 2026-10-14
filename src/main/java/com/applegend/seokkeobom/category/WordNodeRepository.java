package com.applegend.seokkeobom.category;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WordNodeRepository extends JpaRepository<WordNode, Long> {
    List<WordNode> findByCategoryIdOrderByIdAsc(Long categoryId);
    List<WordNode> findByCategoryIdAndDepth(Long categoryId, int depth);
}
