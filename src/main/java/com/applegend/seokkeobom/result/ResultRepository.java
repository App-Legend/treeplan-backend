package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.common.GenerationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {
    Optional<Result> findByIdeaId(Long ideaId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Result r set r.status = :generating, r.updatedAt = :now " +
           "where r.id = :id and (r.status <> :generating or r.updatedAt < :staleBefore)")
    int markGenerating(@Param("id") Long id, @Param("generating") GenerationStatus generating,
                       @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Query("select r from Result r where r.deleted = false and r.sessionId in :sessionIds " +
           "and r.createdAt < :before and (:categoryId = -1 or r.categoryId = :categoryId) " +
           "and (:q = '' or lower(r.title) like :q or lower(r.wordsText) like :q) " +
           "order by r.createdAt desc, r.id desc")
    List<Result> findForHistory(@Param("sessionIds") Collection<Long> sessionIds,
                                @Param("before") Instant before,
                                @Param("categoryId") Long categoryId,
                                @Param("q") String q,
                                Pageable pageable);
}
