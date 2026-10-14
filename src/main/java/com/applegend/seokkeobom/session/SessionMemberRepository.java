package com.applegend.seokkeobom.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionMemberRepository extends JpaRepository<SessionMember, Long> {
    Optional<SessionMember> findBySessionIdAndUserId(Long sessionId, Long userId);
    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
    List<SessionMember> findBySessionIdOrderByJoinedAtAsc(Long sessionId);
    long countBySessionId(Long sessionId);

    @Query("select m.sessionId from SessionMember m where m.userId = :userId")
    List<Long> findSessionIdsByUserId(@Param("userId") Long userId);
}
