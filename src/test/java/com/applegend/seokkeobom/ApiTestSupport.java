package com.applegend.seokkeobom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API별 테스트의 공통 준비 (H2 + 예시 AI 모드).
 * 모든 테스트 클래스가 스프링 컨텍스트와 DB를 공유하므로 이메일은 매번 새로 만든다.
 * 요청은 컨텍스트 경로(/api) 없이 보낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {
    protected static final String PASSWORD = "abcd1234";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    protected static String uniqueEmail() {
        return UUID.randomUUID().toString().substring(0, 12) + "@test.com";
    }

    /** 가입 응답 전체 (accessToken, refreshToken, user) */
    protected JsonNode signupFull(String email) throws Exception {
        return json(perform(post("/auth/signup"), null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"사용자\"}", 201));
    }

    protected String signup() throws Exception {
        return signupFull(uniqueEmail()).get("accessToken").asText();
    }

    protected String guest() throws Exception {
        return json(perform(post("/auth/guest"), null, null, 201)).get("accessToken").asText();
    }

    protected JsonNode createSession(String token) throws Exception {
        return json(perform(post("/sessions"), token, "{\"name\":\"테스트\"}", 201));
    }

    protected void join(String token, String teamCode) throws Exception {
        perform(post("/sessions/join"), token, "{\"teamCode\":\"" + teamCode + "\"}", 200);
    }

    protected long categoryId(int index) throws Exception {
        return json(perform(get("/categories"), null, null, 200)).get(index).get("id").asLong();
    }

    protected JsonNode tree(String token, long categoryId, long sessionId) throws Exception {
        return json(perform(get("/categories/" + categoryId + "/words").param("sessionId", String.valueOf(sessionId)),
                token, null, 200));
    }

    /** 첫 번째 뿌리에서 기본 개수(3개)만큼 가지를 뻗어 뽑기가 가능한 트리를 만든다 */
    protected JsonNode expandFirstRoot(String token, long categoryId, long sessionId) throws Exception {
        long rootId = tree(token, categoryId, sessionId).get("groups").get(0).get("id").asLong();
        return json(perform(post("/words/" + rootId + "/expand"), token, "{\"sessionId\":" + sessionId + "}", 200));
    }

    protected JsonNode draw(String token, long sessionId, long categoryId) throws Exception {
        return json(perform(post("/sessions/" + sessionId + "/draws"), token,
                "{\"categoryIds\":[" + categoryId + "]}", 201));
    }

    protected JsonNode idea(String token, long drawId) throws Exception {
        return json(perform(post("/draws/" + drawId + "/idea"), token, null, 200));
    }

    protected JsonNode result(String token, long ideaId) throws Exception {
        return json(perform(post("/ideas/" + ideaId + "/result"), token, null, 200));
    }

    /** 세션 생성 → 트리 뻗기 → 뽑기까지 끝낸 상태 */
    protected record Drawn(String token, long sessionId, String teamCode, long categoryId, long drawId) {}

    protected Drawn drawn() throws Exception {
        String token = signup();
        JsonNode s = createSession(token);
        long sessionId = s.get("sessionId").asLong();
        long cat = categoryId(0);
        expandFirstRoot(token, cat, sessionId);
        return new Drawn(token, sessionId, s.get("teamCode").asText(), cat, draw(token, sessionId, cat).get("drawId").asLong());
    }

    protected MvcResult perform(MockHttpServletRequestBuilder req, String token, String body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req).andExpect(status().is(expected)).andReturn();
    }

    /** 실패 응답의 code 필드 */
    protected String code(MvcResult r) throws Exception {
        return json(r).get("code").asText();
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
