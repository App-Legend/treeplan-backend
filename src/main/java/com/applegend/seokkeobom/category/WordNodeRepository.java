package com.applegend.seokkeobom.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordNodeRepository extends JpaRepository<WordNode, Long> {
    /** 세션 공용 노드(뿌리) */
    List<WordNode> findByCategoryIdAndSessionIdIsNullOrderByIdAsc(Long categoryId);

    /** 이 세션에서 자란 단어 */
    List<WordNode> findByCategoryIdAndSessionIdOrderByIdAsc(Long categoryId, Long sessionId);

    long countByRootIdAndSessionId(Long rootId, Long sessionId);

    /** 이 세션에서 뿌리 하나에 가지를 뻗은 횟수 */
    @Query("select coalesce(max(n.expandNo), 0) from WordNode n where n.rootId = :rootId and n.sessionId = :sessionId")
    int countExpands(@Param("rootId") Long rootId, @Param("sessionId") Long sessionId);

    @Modifying
    @Query("delete from WordNode n where n.categoryId = :categoryId and n.sessionId is null")
    int deleteSharedNodes(@Param("categoryId") Long categoryId);
}
