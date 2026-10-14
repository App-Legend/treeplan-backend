package com.applegend.seokkeobom.draw;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DrawRepository extends JpaRepository<Draw, Long> {
    long countBySessionId(Long sessionId);
    Optional<Draw> findFirstBySessionIdAndUserIdOrderByIdDesc(Long sessionId, Long userId);
}
