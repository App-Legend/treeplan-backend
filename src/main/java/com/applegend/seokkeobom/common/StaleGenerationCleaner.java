package com.applegend.seokkeobom.common;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버가 켜질 때, 이전 실행 중에 '생성 중'으로 멈춘 아이디어·실행안을 '실패'로 바꾼다.
 * 서버가 꺼지면 진행 중이던 AI 요청도 사라지므로, 그대로 두면 다시 시도해도 '이미 만들고 있어요'로 막힌다.
 */
@Component
public class StaleGenerationCleaner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StaleGenerationCleaner.class);
    static final String REASON = "서버가 다시 시작되어 생성이 중단됐어요. 다시 시도해 주세요.";

    private final EntityManager em;

    public StaleGenerationCleaner(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int ideas = fail("Idea");
        int results = fail("Result");
        if (ideas + results > 0) {
            log.info("멈춰 있던 생성 작업을 실패로 정리했습니다. (아이디어 {}개, 실행안 {}개)", ideas, results);
        }
    }

    private int fail(String entity) {
        return em.createQuery("update " + entity + " e set e.status = :failed, e.failureReason = :reason "
                        + "where e.status = :generating")
                .setParameter("failed", GenerationStatus.FAILED)
                .setParameter("generating", GenerationStatus.GENERATING)
                .setParameter("reason", REASON)
                .executeUpdate();
    }
}
