package com.applegend.seokkeobom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 핵심 흐름 통합 테스트 (H2 + 예시 AI 모드).
 * 카테고리 선택 → 단어 3개 → 창조(아이디어) → 결과물 → 수정·내보내기 → 히스토리 → 삭제
 * 그리고 비기능 요구사항(데이터): 다른 분야 단어가 섞이지 않는지, 3개 미만으로 생성되지 않는지.
 * 테스트 요청은 컨텍스트 경로(/api) 없이 보낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void coreFlow() throws Exception {
        String token = signup("owner@test.com", "방장");

        // 1. 세션 생성 (같은 멱등 키 → 같은 세션)
        JsonNode s1 = json(perform(post("/sessions").header("Idempotency-Key", "k1"), token, "{\"name\":\"테스트\"}", 201));
        JsonNode s2 = json(perform(post("/sessions").header("Idempotency-Key", "k1"), token, "{\"name\":\"테스트\"}", 201));
        assertThat(s1.get("sessionId").asLong()).isEqualTo(s2.get("sessionId").asLong());
        long sessionId = s1.get("sessionId").asLong();
        String teamCode = s1.get("teamCode").asText();

        // 2. 카테고리와 단어 트리
        JsonNode cats = json(perform(get("/categories"), token, null, 200));
        assertThat(cats).hasSize(3);
        long foodId = cats.get(0).get("id").asLong();
        long programId = cats.get(1).get("id").asLong();
        JsonNode tree = json(perform(get("/categories/" + foodId + "/words"), token, null, 200));
        Set<String> foodWords = new HashSet<>();
        tree.get("groups").forEach(g -> g.get("children").forEach(w -> foodWords.add(w.get("text").asText())));
        assertThat(foodWords).hasSizeGreaterThan(30);

        // 3. 단어 추출 반복: 항상 3개, 항상 음식 카테고리 단어만
        JsonNode draw = null;
        for (int i = 0; i < 20; i++) {
            draw = json(perform(post("/sessions/" + sessionId + "/draws"), token,
                    "{\"categoryIds\":[" + foodId + "]}", 201));
            List<String> words = om.convertValue(draw.get("words"), List.class);
            assertThat(words).hasSize(3).doesNotHaveDuplicates();
            assertThat(foodWords).containsAll(words);
        }
        // 고정 2개 + 다시 뽑기
        String f1 = draw.get("words").get(0).asText();
        String f2 = draw.get("words").get(1).asText();
        JsonNode fixedDraw = json(perform(post("/sessions/" + sessionId + "/draws"), token,
                "{\"categoryIds\":[" + foodId + "],\"fixedWords\":[\"" + f1 + "\",\"" + f2 + "\"]}", 201));
        assertThat(om.convertValue(fixedDraw.get("words"), List.class)).contains(f1, f2);
        // 트리에서 3개를 모두 고정하면 그 조합 그대로 저장
        String f3 = draw.get("words").get(2).asText();
        JsonNode allFixed = json(perform(post("/sessions/" + sessionId + "/draws"), token,
                "{\"categoryIds\":[" + foodId + "],\"fixedWords\":[\"" + f1 + "\",\"" + f2 + "\",\"" + f3 + "\"]}", 201));
        assertThat(om.convertValue(allFixed.get("words"), List.class)).containsExactly(f1, f2, f3);
        // 여러 카테고리 혼합 금지
        perform(post("/sessions/" + sessionId + "/draws"), token,
                "{\"categoryIds\":[" + foodId + "," + programId + "]}", 400);
        // 제외로 풀이 3개 미만이 되면 생성하지 않음
        String excludeAll = om.writeValueAsString(foodWords);
        perform(post("/sessions/" + sessionId + "/draws"), token,
                "{\"categoryIds\":[" + foodId + "],\"excludedWords\":" + excludeAll + "}", 422);

        // 4. 창조 → 아이디어
        long drawId = fixedDraw.get("drawId").asLong();
        JsonNode idea = json(perform(post("/draws/" + drawId + "/idea"), token, null, 200));
        assertThat(idea.get("status").asText()).isEqualTo("SUCCESS");
        long ideaId = idea.get("ideaId").asLong();
        perform(put("/ideas/" + ideaId + "/bookmark"), token, "{\"bookmarked\":true}", 200);

        // 5. 결과물: 양식 항목 + 사용 단어 + 조합 이유 포함
        JsonNode result = json(perform(post("/ideas/" + ideaId + "/result"), token, null, 200));
        assertThat(result.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.get("words")).hasSize(3);
        Set<String> keys = new HashSet<>();
        result.get("items").forEach(it -> keys.add(it.get("key").asText()));
        assertThat(keys).contains("concept", "ingredients", "steps", "result_caution", "reason");
        long resultId = result.get("resultId").asLong();
        // 같은 아이디어로 다시 요청 → 기존 결과물 반환
        JsonNode again = json(perform(post("/ideas/" + ideaId + "/result"), token, null, 200));
        assertThat(again.get("resultId").asLong()).isEqualTo(resultId);

        // 수정: 양식에 없는 항목은 거부
        perform(patch("/results/" + resultId), token, "{\"items\":{\"unknown\":\"x\"}}", 400);
        JsonNode edited = json(perform(patch("/results/" + resultId), token, "{\"items\":{\"concept\":\"직접 고친 콘셉트\"}}", 200));
        assertThat(edited.get("edited").asBoolean()).isTrue();

        // 보완 재생성
        perform(post("/results/" + resultId + "/regenerate"), token, "{\"feedback\":\"분량을 더 자세히\"}", 200);

        // 내보내기
        MvcResult md = perform(get("/results/" + resultId + "/export").param("format", "md"), token, null, 200);
        assertThat(md.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("조합 이유");
        perform(get("/results/" + resultId + "/export").param("format", "docx"), token, null, 400);

        // 6. 팀원 입장 → 같은 결과물 열람 가능, 삭제는 불가
        String memberToken = signup("member@test.com", "팀원");
        perform(post("/sessions/join"), memberToken, "{\"teamCode\":\"" + teamCode.toLowerCase() + "\"}", 200);
        perform(get("/results/" + resultId), memberToken, null, 200);
        perform(delete("/results/" + resultId), memberToken, null, 403);

        // 외부인은 열람 불가
        String outsider = signup("outsider@test.com", "외부인");
        perform(get("/results/" + resultId), outsider, null, 403);

        // 7. 히스토리: 결과물 + 보관 아이디어
        JsonNode history = json(perform(get("/history"), token, null, 200));
        Set<String> types = new HashSet<>();
        history.get("items").forEach(it -> types.add(it.get("type").asText()));
        assertThat(types).contains("RESULT", "IDEA");

        // 삭제(방장) 후 조회 불가
        perform(delete("/results/" + resultId), token, null, 200);
        perform(get("/results/" + resultId), token, null, 404);

        // 방장 나가기 불가, 세션 종료 후 입장 불가
        perform(delete("/sessions/" + sessionId + "/members/me"), token, null, 409);
        perform(post("/sessions/" + sessionId + "/close"), token, null, 200);
        perform(post("/sessions/join"), outsider, "{\"teamCode\":\"" + teamCode + "\"}", 409);
    }

    @Test
    void authRules() throws Exception {
        perform(get("/categories"), null, null, 401);
        signup("a@test.com", "에이");
        // 같은 이메일 중복 가입 거부
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@test.com\",\"password\":\"abcd1234\",\"name\":\"x\"}"))
                .andExpect(status().isConflict());
        // 약한 비밀번호 거부
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"b@test.com\",\"password\":\"short\",\"name\":\"x\"}"))
                .andExpect(status().isBadRequest());
        // 비밀번호 찾기는 계정 존재 여부와 관계없이 같은 응답
        mvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\"}")).andExpect(status().isAccepted());
        // 리프레시 토큰은 한 번만 쓸 수 있음
        MvcResult login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@test.com\",\"password\":\"abcd1234\"}")).andExpect(status().isOk()).andReturn();
        String refresh = json(login).get("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + refresh + "\"}";
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String signup(String email, String name) throws Exception {
        MvcResult r = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(r).get("accessToken").asText();
    }

    private MvcResult perform(MockHttpServletRequestBuilder req, String token, String body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req).andExpect(status().is(expected)).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
