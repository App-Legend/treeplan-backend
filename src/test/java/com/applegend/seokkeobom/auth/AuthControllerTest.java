package com.applegend.seokkeobom.auth;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** /auth/** */
class AuthControllerTest extends ApiTestSupport {

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    // ---------- POST /auth/guest ----------

    @Test
    void guestIssuesTokenWithoutEmail() throws Exception {
        JsonNode r = json(perform(post("/auth/guest"), null, null, 201));
        assertThat(r.get("accessToken").asText()).isNotBlank();
        assertThat(r.get("user").get("guest").asBoolean()).isTrue();
        assertThat(r.get("user").get("email").isNull()).isTrue();
    }

    // ---------- POST /auth/signup ----------

    @Test
    void signupIssuesTokens() throws Exception {
        String email = uniqueEmail();
        JsonNode r = signupFull(email);
        assertThat(r.get("accessToken").asText()).isNotBlank();
        assertThat(r.get("refreshToken").asText()).isNotBlank();
        assertThat(r.get("expiresIn").asLong()).isPositive();
        assertThat(r.get("user").get("email").asText()).isEqualTo(email);
        assertThat(r.get("user").get("guest").asBoolean()).isFalse();
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        signupFull(email);
        String body = "{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"name\":\"x\"}";
        assertThat(code(perform(post("/auth/signup"), null, body, 409))).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void signupRejectsWeakPasswordAndBadInput() throws Exception {
        String weak = "{\"email\":\"" + uniqueEmail() + "\",\"password\":\"abcdefgh\",\"name\":\"x\"}";
        assertThat(code(perform(post("/auth/signup"), null, weak, 400))).isEqualTo("WEAK_PASSWORD");
        String badEmail = "{\"email\":\"not-email\",\"password\":\"abcd1234\",\"name\":\"x\"}";
        assertThat(code(perform(post("/auth/signup"), null, badEmail, 400))).isEqualTo("VALIDATION");
        String noName = "{\"email\":\"" + uniqueEmail() + "\",\"password\":\"abcd1234\",\"name\":\"\"}";
        assertThat(code(perform(post("/auth/signup"), null, noName, 400))).isEqualTo("VALIDATION");
    }

    // ---------- POST /auth/login ----------

    @Test
    void loginWithCorrectPassword() throws Exception {
        String email = uniqueEmail();
        signupFull(email);
        JsonNode r = json(perform(post("/auth/login"), null, loginBody(email, PASSWORD), 200));
        assertThat(r.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailTheSameWay() throws Exception {
        String email = uniqueEmail();
        signupFull(email);
        JsonNode wrong = json(perform(post("/auth/login"), null, loginBody(email, "wrong1234"), 401));
        JsonNode unknown = json(perform(post("/auth/login"), null, loginBody(uniqueEmail(), PASSWORD), 401));
        assertThat(wrong.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        // 계정 존재 여부를 드러내지 않는다
        assertThat(wrong).isEqualTo(unknown);
    }

    @Test
    void loginLocksAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        signupFull(email);
        for (int i = 0; i < AuthService.MAX_FAILED_LOGINS; i++) {
            perform(post("/auth/login"), null, loginBody(email, "wrong1234"), 401);
        }
        // 잠긴 동안은 맞는 비밀번호도 거부
        assertThat(code(perform(post("/auth/login"), null, loginBody(email, PASSWORD), 429))).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    // ---------- POST /auth/refresh ----------

    @Test
    void refreshRotatesToken() throws Exception {
        String refresh = signupFull(uniqueEmail()).get("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + refresh + "\"}";
        JsonNode next = json(perform(post("/auth/refresh"), null, body, 200));
        assertThat(next.get("refreshToken").asText()).isNotEqualTo(refresh);
        // 한 번 쓴 토큰은 다시 못 쓴다
        assertThat(code(perform(post("/auth/refresh"), null, body, 401))).isEqualTo("INVALID_REFRESH");
    }

    @Test
    void refreshRejectsUnknownOrBlankToken() throws Exception {
        assertThat(code(perform(post("/auth/refresh"), null, "{\"refreshToken\":\"nope\"}", 401))).isEqualTo("INVALID_REFRESH");
        assertThat(code(perform(post("/auth/refresh"), null, "{\"refreshToken\":\"\"}", 400))).isEqualTo("VALIDATION");
    }

    // ---------- POST /auth/password-reset ----------

    @Test
    void passwordResetAnswersTheSameForAnyEmail() throws Exception {
        String email = uniqueEmail();
        signupFull(email);
        JsonNode known = json(perform(post("/auth/password-reset"), null, "{\"email\":\"" + email + "\"}", 202));
        JsonNode unknown = json(perform(post("/auth/password-reset"), null, "{\"email\":\"" + uniqueEmail() + "\"}", 202));
        assertThat(known).isEqualTo(unknown);
        assertThat(code(perform(post("/auth/password-reset"), null, "{\"email\":\"bad\"}", 400))).isEqualTo("VALIDATION");
    }
}
