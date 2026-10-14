package com.applegend.seokkeobom.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface IdeaSessionRepository extends JpaRepository<IdeaSession, Long> {
    Optional<IdeaSession> findByTeamCode(String teamCode);
    boolean existsByTeamCode(String teamCode);
    Optional<IdeaSession> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    @Transactional
    @Modifying
    @Query("update IdeaSession s set s.status = :expired where s.status = :active and s.lastActivityAt < :threshold")
    int expireInactive(@Param("threshold") Instant threshold,
                       @Param("active") SessionStatus active,
                       @Param("expired") SessionStatus expired);
}
