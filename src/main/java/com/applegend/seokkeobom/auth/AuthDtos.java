package com.applegend.seokkeobom.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 40, message = "이름은 40자 이하로 입력해 주세요.") String name) {}

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {}

    public record RefreshRequest(@NotBlank(message = "리프레시 토큰이 없습니다.") String refreshToken) {}

    public record PasswordResetRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {}

    public record UserInfo(Long id, String email, String name) {
        static UserInfo of(User u) { return new UserInfo(u.getId(), u.getEmail(), u.getName()); }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserInfo user) {}

    public record MessageResponse(String message) {}
}
