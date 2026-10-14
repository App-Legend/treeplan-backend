package com.applegend.seokkeobom.auth;

import com.applegend.seokkeobom.auth.AuthDtos.*;
import com.applegend.seokkeobom.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final String INVALID_LOGIN = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    @Transactional
    public TokenResponse signup(SignupRequest req) {
        String email = normalize(req.email());
        validatePassword(req.password());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password()), req.name().trim()));
        return issueTokens(user);
    }

    /** 실패 횟수 기록이 롤백되지 않도록 예외 대신 결과 객체로 분기한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_LOGIN));
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "로그인 시도가 많아 잠시 제한되었습니다. 5분 뒤 다시 시도해 주세요.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int failed = user.getFailedLoginCount() + 1;
            if (failed >= MAX_FAILED_LOGINS) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(now.plus(LOCK_DURATION));
            } else {
                user.setFailedLoginCount(failed);
            }
            users.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_LOGIN);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        users.save(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthService::invalidRefresh);
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw invalidRefresh();
        }
        token.revoke(); // 회전: 한 번 쓴 리프레시 토큰은 폐기
        refreshTokens.save(token);
        User user = users.findById(token.getUserId()).orElseThrow(AuthService::invalidRefresh);
        return issueTokens(user);
    }

    /** 계정 존재 여부를 드러내지 않도록 항상 같은 응답을 준다. */
    public MessageResponse requestPasswordReset(String email) {
        boolean exists = users.existsByEmail(normalize(email));
        // TODO: 메일 발송 서비스가 아키텍처에 없어 실제 발송은 하지 않는다. (README '확인해야 할 부분' 참고)
        log.info("Password reset requested (account exists: {})", exists);
        return new MessageResponse("입력한 이메일로 안내를 보냈습니다. 메일이 오지 않으면 주소를 다시 확인해 주세요.");
    }

    private TokenResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(sha256(raw), user.getId(), Instant.now().plus(refreshTtl)));
        return new TokenResponse(jwtProvider.createAccessToken(user.getId()), raw,
                jwtProvider.accessTtlSeconds(), UserInfo.of(user));
    }

    private static void validatePassword(String pw) {
        if (pw.length() < 8 || pw.length() > 64 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
            throw ApiException.badRequest("WEAK_PASSWORD", "비밀번호는 영문과 숫자를 포함해 8~64자로 입력해 주세요.");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
