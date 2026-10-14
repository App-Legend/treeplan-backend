package com.applegend.seokkeobom.config;

import com.applegend.seokkeobom.auth.JwtProvider;
import com.applegend.seokkeobom.session.SessionMemberRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 연결 시 JWT를 확인하고, 구독은 그 세션의 참여자에게만 허용한다.
 * 클라이언트는 CONNECT 프레임 헤더에 Authorization: Bearer <토큰>을 보낸다.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/sessions/(\\d+)/events$");

    private final JwtProvider jwtProvider;
    private final SessionMemberRepository members;

    public StompAuthInterceptor(JwtProvider jwtProvider, SessionMemberRepository members) {
        this.jwtProvider = jwtProvider;
        this.members = members;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
            Long userId = token == null ? null : jwtProvider.parseUserId(token).orElse(null);
            if (userId == null) throw new MessageDeliveryException("UNAUTHORIZED");
            accessor.setUser(new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            String dest = accessor.getDestination();
            if (user == null || dest == null) throw new MessageDeliveryException("UNAUTHORIZED");
            Matcher m = SESSION_TOPIC.matcher(dest);
            if (!m.matches()) throw new MessageDeliveryException("FORBIDDEN_DESTINATION");
            Long sessionId = Long.valueOf(m.group(1));
            Long userId = Long.valueOf(user.getName());
            if (!members.existsBySessionIdAndUserId(sessionId, userId)) {
                throw new MessageDeliveryException("NOT_MEMBER");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            // 이 채널은 서버 → 클라이언트 알림 전용이다.
            throw new MessageDeliveryException("READ_ONLY_CHANNEL");
        }
        return message;
    }
}
