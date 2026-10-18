package com.applegend.seokkeobom.auth;

import com.applegend.seokkeobom.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 게스트(비로그인) 여부 확인. 히스토리·팀 코드처럼 로그인이 필요한 기능 앞에서 쓴다. */
@Component
public class Accounts {
    public static final String LOGIN_PROMPT = "히스토리로 남기고 싶다면 로그인해 주세요. 나가면 저장되지 않습니다.";

    private final UserRepository users;

    public Accounts(UserRepository users) {
        this.users = users;
    }

    public boolean isGuest(Long userId) {
        return users.findById(userId).map(User::isGuest).orElse(false);
    }

    public void requireRegistered(Long userId, String message) {
        if (isGuest(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOGIN_REQUIRED", message);
        }
    }
}
