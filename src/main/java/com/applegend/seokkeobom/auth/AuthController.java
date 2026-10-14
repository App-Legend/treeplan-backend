package com.applegend.seokkeobom.auth;

import com.applegend.seokkeobom.auth.AuthDtos.*;
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

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
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
