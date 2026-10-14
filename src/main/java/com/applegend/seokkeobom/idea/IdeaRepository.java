package com.applegend.seokkeobom.idea;

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

public interface IdeaRepository extends JpaRepository<Idea, Long> {
    Optional<Idea> findByDrawId(Long drawId);

    /** 동시에 두 번 눌러도 한 번만 '생성 중'으로 바뀌게 하는 조건부 갱신 (중복 호출 방지) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Idea i set i.status = :generating, i.failureReason = null, i.updatedAt = :now " +
           "where i.id = :id and (i.status <> :generating or i.updatedAt < :staleBefore)")
    int markGenerating(@Param("id") Long id, @Param("generating") GenerationStatus generating,
                       @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Query("select i from Idea i where i.bookmarked = true and i.sessionId in :sessionIds " +
           "and i.bookmarkedAt < :before and (:categoryId = -1 or i.categoryId = :categoryId) " +
           "and (:q = '' or lower(i.title) like :q or lower(i.summary) like :q) " +
           "order by i.bookmarkedAt desc, i.id desc")
    List<Idea> findBookmarkedForHistory(@Param("sessionIds") Collection<Long> sessionIds,
                                        @Param("before") Instant before,
                                        @Param("categoryId") Long categoryId,
                                        @Param("q") String q,
                                        Pageable pageable);
}
