package com.applegend.seokkeobom.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 30일 무활동 세션을 하루 한 번 '만료'로 바꾸는 예약 작업 (API가 아님) */
@Component
public class SessionExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(SessionExpiryJob.class);

    private final IdeaSessionRepository sessions;
    private final Duration inactiveLimit;

    public SessionExpiryJob(IdeaSessionRepository sessions, @Value("${app.session.inactive-days}") long inactiveDays) {
        this.sessions = sessions;
        this.inactiveLimit = Duration.ofDays(inactiveDays);
    }

    @Scheduled(cron = "${app.session.expiry-cron}", zone = "Asia/Seoul")
    @Transactional
    public void expireInactiveSessions() {
        int count = sessions.expireInactive(Instant.now().minus(inactiveLimit), SessionStatus.ACTIVE, SessionStatus.EXPIRED);
        if (count > 0) log.info("Expired {} inactive sessions", count);
    }
}
