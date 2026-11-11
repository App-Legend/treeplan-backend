package com.applegend.seokkeobom.session;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** /sessions/** */
class SessionControllerTest extends ApiTestSupport {

    // ---------- POST /sessions ----------

    @Test
    void createReturnsTeamCode() throws Exception {
        JsonNode s = createSession(signup());
        assertThat(s.get("sessionId").asLong()).isPositive();
        assertThat(s.get("name").asText()).isEqualTo("테스트");
        assertThat(s.get("teamCode").asText()).isNotBlank();
    }

    @Test
    void createWithoutBodyUsesDefaultName() throws Exception {
        JsonNode s = json(perform(post("/sessions"), signup(), null, 201));
        assertThat(s.get("name").asText()).isEqualTo("새 발상 세션");
    }

    @Test
    void createIsIdempotentPerKey() throws Exception {
        String token = signup();
        long a = json(perform(post("/sessions").header("Idempotency-Key", "same"), token, "{}", 201)).get("sessionId").asLong();
        long b = json(perform(post("/sessions").header("Idempotency-Key", "same"), token, "{}", 201)).get("sessionId").asLong();
        long c = json(perform(post("/sessions").header("Idempotency-Key", "other"), token, "{}", 201)).get("sessionId").asLong();
        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }

    @Test
    void createRejectsBadInput() throws Exception {
        String token = signup();
        assertThat(code(perform(post("/sessions"), token, "{\"name\":\"" + "가".repeat(41) + "\"}", 400))).isEqualTo("INVALID_NAME");
        assertThat(code(perform(post("/sessions").header("Idempotency-Key", "k".repeat(65)), token, "{}", 400)))
                .isEqualTo("BAD_IDEMPOTENCY_KEY");
        perform(post("/sessions"), null, "{}", 401);
    }

    @Test
    void guestSessionHasNoTeamCode() throws Exception {
        assertThat(createSession(guest()).get("teamCode").isNull()).isTrue();
    }

    // ---------- POST /sessions/join ----------

    @Test
    void joinNormalizesTeamCode() throws Exception {
        JsonNode s = createSession(signup());
        String code = s.get("teamCode").asText();
        String messy = " " + code.substring(0, 3).toLowerCase() + "-" + code.substring(3).toLowerCase() + " ";
        JsonNode r = json(perform(post("/sessions/join"), signup(), "{\"teamCode\":\"" + messy + "\"}", 200));
        assertThat(r.get("sessionId").asLong()).isEqualTo(s.get("sessionId").asLong());
    }

    @Test
    void joinRejectsUnknownBlankAndGuest() throws Exception {
        String token = signup();
        assertThat(code(perform(post("/sessions/join"), token, "{\"teamCode\":\"ZZZZZZZZ\"}", 404))).isEqualTo("NOT_FOUND");
        assertThat(code(perform(post("/sessions/join"), token, "{\"teamCode\":\"\"}", 400))).isEqualTo("VALIDATION");
        String teamCode = createSession(signup()).get("teamCode").asText();
        perform(post("/sessions/join"), guest(), "{\"teamCode\":\"" + teamCode + "\"}", 403);
    }

    @Test
    void joinRejectsClosedSession() throws Exception {
        String owner = signup();
        JsonNode s = createSession(owner);
        perform(post("/sessions/" + s.get("sessionId").asLong() + "/close"), owner, null, 200);
        assertThat(code(perform(post("/sessions/join"), signup(), "{\"teamCode\":\"" + s.get("teamCode").asText() + "\"}", 409)))
                .isEqualTo("CLOSED");
    }

    // ---------- GET /sessions/{id} ----------

    @Test
    void infoListsMembersAndMyRole() throws Exception {
        String owner = signup();
        JsonNode s = createSession(owner);
        long id = s.get("sessionId").asLong();
        String member = signup();
        join(member, s.get("teamCode").asText());

        JsonNode asOwner = json(perform(get("/sessions/" + id), owner, null, 200));
        assertThat(asOwner.get("members")).hasSize(2);
        assertThat(asOwner.get("myRole").asText()).isEqualTo("OWNER");
        assertThat(asOwner.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(json(perform(get("/sessions/" + id), member, null, 200)).get("myRole").asText()).isEqualTo("MEMBER");
    }

    @Test
    void infoRejectsStrangerAndMissingSession() throws Exception {
        long id = createSession(signup()).get("sessionId").asLong();
        String stranger = signup();
        assertThat(code(perform(get("/sessions/" + id), stranger, null, 403))).isEqualTo("NOT_MEMBER");
        assertThat(code(perform(get("/sessions/999999"), stranger, null, 404))).isEqualTo("SESSION_NOT_FOUND");
    }

    // ---------- DELETE /sessions/{id}/members/me ----------

    @Test
    void memberCanLeaveButOwnerCannot() throws Exception {
        String owner = signup();
        JsonNode s = createSession(owner);
        long id = s.get("sessionId").asLong();
        String member = signup();
        join(member, s.get("teamCode").asText());

        assertThat(json(perform(delete("/sessions/" + id + "/members/me"), member, null, 200)).get("success").asBoolean()).isTrue();
        perform(get("/sessions/" + id), member, null, 403);
        assertThat(code(perform(delete("/sessions/" + id + "/members/me"), owner, null, 409))).isEqualTo("OWNER_CANNOT_LEAVE");
    }

    // ---------- POST /sessions/{id}/close ----------

    @Test
    void onlyOwnerClosesOnce() throws Exception {
        String owner = signup();
        JsonNode s = createSession(owner);
        long id = s.get("sessionId").asLong();
        String member = signup();
        join(member, s.get("teamCode").asText());

        assertThat(code(perform(post("/sessions/" + id + "/close"), member, null, 403))).isEqualTo("NOT_OWNER");
        assertThat(json(perform(post("/sessions/" + id + "/close"), owner, null, 200)).get("closedAt").asText()).isNotBlank();
        assertThat(code(perform(post("/sessions/" + id + "/close"), owner, null, 409))).isEqualTo("ALREADY_CLOSED");
        assertThat(json(perform(get("/sessions/" + id), owner, null, 200)).get("status").asText()).isEqualTo("CLOSED");
    }
}
