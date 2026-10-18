package com.applegend.seokkeobom.auth;

import com.applegend.seokkeobom.auth.AuthDtos.*;
import com.applegend.seokkeobom.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 로그인 없이 시작: 임시(게스트) 토큰 발급 */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse guest() {
        return authService.guest();
    }

    /** 게스트 토큰을 Authorization 헤더로 함께 보내면 게스트 작업이 새 계정으로 옮겨진다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req, CurrentUser.optionalId().orElse(null));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req, CurrentUser.optionalId().orElse(null));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse passwordReset(@Valid @RequestBody PasswordResetRequest req) {
        return authService.requestPasswordReset(req.email());
    }
}
