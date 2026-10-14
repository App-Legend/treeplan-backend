package com.applegend.seokkeobom.session;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** 실시간 채널(화살표 ⑨): 서버가 세션 참여자에게 먼저 보내는 알림 */
@Component
public class SessionEventPublisher {
    public static final String MEMBER_JOINED = "MEMBER_JOINED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String RESULT_CREATED = "RESULT_CREATED";
    public static final String SESSION_CLOSED = "SESSION_CLOSED";

    private final SimpMessagingTemplate messaging;

    public SessionEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public record SessionEvent(String type, Long sessionId, String at, Map<String, Object> data) {}

    public static String destination(Long sessionId) {
        return "/topic/sessions/" + sessionId + "/events";
    }

    public void publish(Long sessionId, String type, Map<String, Object> data) {
        messaging.convertAndSend(destination(sessionId), new SessionEvent(type, sessionId, Instant.now().toString(), data));
    }
}
