package com.applegend.seokkeobom.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    @Test
    void roundTripsUserId() {
        JwtProvider jwt = new JwtProvider(SECRET, 30);
        assertThat(jwt.parseUserId(jwt.createAccessToken(42L))).contains(42L);
        assertThat(jwt.accessTtlSeconds()).isEqualTo(1800);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProvider("too-short", 30)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsForeignExpiredAndGarbageTokens() {
        JwtProvider jwt = new JwtProvider(SECRET, 30);
        String foreign = new JwtProvider("another-secret-another-secret-another-1", 30).createAccessToken(1L);
        String expired = new JwtProvider(SECRET, -1).createAccessToken(1L);
        assertThat(jwt.parseUserId(foreign)).isEmpty();
        assertThat(jwt.parseUserId(expired)).isEmpty();
        assertThat(jwt.parseUserId("not-a-jwt")).isEmpty();
        assertThat(jwt.parseUserId("")).isEmpty();
    }
}
