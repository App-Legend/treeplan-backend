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

        // 2. 카테고리와 단어 트리: 뿌리(고정 하위 카테고리) 3개, 처음에는 단어 없음
        JsonNode cats = json(perform(get("/categories"), null, null, 200));
        assertThat(cats).hasSize(3);
        long foodId = cats.get(0).get("id").asLong();
        long programId = cats.get(1).get("id").asLong();
        JsonNode tree = json(perform(get("/categories/" + foodId + "/words").param("sessionId", String.valueOf(sessionId)), token, null, 200));
        assertThat(tree.get("groups")).hasSize(3);
        assertThat(tree.get("wordCount").asInt()).isZero();
        assertThat(tree.get("maxExpandsPerRoot").asInt()).isEqualTo(10);
        // 트리가 비어 있으면 뽑기 불가
        perform(post("/sessions/" + sessionId + "/draws"), token, "{\"categoryIds\":[" + foodId + "]}", 422);

        // AI 가지 뻗기: 하위 카테고리(뿌리)마다 10번까지 (여기서는 한 번에 1개씩 10번)
        long rootId = tree.get("groups").get(0).get("id").asLong();
        JsonNode firstExpand = null;
        for (int i = 0; i < 10; i++) {
            JsonNode ex = json(perform(post("/words/" + rootId + "/expand"), token,
                    "{\"sessionId\":" + sessionId + ",\"count\":1}", 200));
            assertThat(ex.get("words")).hasSize(1);
            assertThat(ex.get("expandCount").asInt()).isEqualTo(i + 1);
            if (firstExpand == null) firstExpand = ex;
        }
        JsonNode full = json(perform(post("/words/" + rootId + "/expand"), token, "{\"sessionId\":" + sessionId + "}", 409));
        assertThat(full.get("code").asText()).isEqualTo("ROOT_FULL");
        // 단어에서 더 뻗기(깊이 3)는 다른 뿌리에서
        long secondRoot = tree.get("groups").get(1).get("id").asLong();
        JsonNode ex2 = json(perform(post("/words/" + secondRoot + "/expand"), token, "{\"sessionId\":" + sessionId + ",\"count\":3}", 200));
        long childWord = ex2.get("words").get(0).get("id").asLong();
        JsonNode deeper = json(perform(post("/words/" + childWord + "/expand"), token, "{\"sessionId\":" + sessionId + ",\"count\":2}", 200));
        assertThat(deeper.get("rootId").asLong()).isEqualTo(secondRoot);
        assertThat(deeper.get("rootWordCount").asInt()).isEqualTo(5);
        assertThat(deeper.get("expandCount").asInt()).isEqualTo(2);
        // 다른 세션 참여자가 아니면 트리 조회 불가
        String stranger = signup("stranger@test.com", "남");
        perform(get("/categories/" + foodId + "/words").param("sessionId", String.valueOf(sessionId)), stranger, null, 403);

        tree = json(perform(get("/categories/" + foodId + "/words").param("sessionId", String.valueOf(sessionId)), token, null, 200));
        assertThat(tree.get("groups").get(0).get("wordCount").asInt()).isEqualTo(10);
        assertThat(tree.get("groups").get(0).get("expandCount").asInt()).isEqualTo(10);
        assertThat(tree.get("groups").get(0).get("remaining").asInt()).isZero();
        Set<String> foodWords = new HashSet<>();
        collect(tree.get("groups"), foodWords);
        assertThat(foodWords).hasSize(15).doesNotHaveDuplicates();

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

    /** 로그인 없이 핵심 흐름 → 결과 저장 안 됨(로그인 권유) → 가입하면 히스토리로 옮겨짐 */
    @Test
    void guestFlow() throws Exception {
        String guest = json(mvc.perform(post("/auth/guest")).andExpect(status().isCreated()).andReturn()).get("accessToken").asText();
        JsonNode s = json(perform(post("/sessions"), guest, "{\"name\":\"게스트\"}", 201));
        long sessionId = s.get("sessionId").asLong();
        assertThat(s.get("teamCode").isNull()).isTrue();
        long catId = json(perform(get("/categories"), null, null, 200)).get(1).get("id").asLong();
        JsonNode tree = json(perform(get("/categories/" + catId + "/words").param("sessionId", String.valueOf(sessionId)), guest, null, 200));
        perform(post("/words/" + tree.get("groups").get(0).get("id").asLong() + "/expand"), guest, "{\"sessionId\":" + sessionId + "}", 200);
        JsonNode draw = json(perform(post("/sessions/" + sessionId + "/draws"), guest, "{\"categoryIds\":[" + catId + "]}", 201));
        JsonNode idea = json(perform(post("/draws/" + draw.get("drawId").asLong() + "/idea"), guest, null, 200));
        JsonNode result = json(perform(post("/ideas/" + idea.get("ideaId").asLong() + "/result"), guest, null, 200));
        assertThat(result.get("saved").asBoolean()).isFalse();
        assertThat(result.get("loginPrompt").asText()).contains("로그인");
        // 게스트는 히스토리·팀 코드 입장 불가
        assertThat(json(perform(get("/history"), guest, null, 403)).get("code").asText()).isEqualTo("LOGIN_REQUIRED");
        perform(post("/sessions/join"), guest, "{\"teamCode\":\"ABCDEF\"}", 403);

        // 팝업에서 가입 (게스트 토큰을 함께 보냄) → 결과가 계정 히스토리로
        MvcResult r = mvc.perform(post("/auth/signup").header("Authorization", "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"saved@test.com\",\"password\":\"abcd1234\",\"name\":\"저장\"}"))
                .andExpect(status().isCreated()).andReturn();
        String user = json(r).get("accessToken").asText();
        assertThat(json(r).get("user").get("guest").asBoolean()).isFalse();
        JsonNode history = json(perform(get("/history"), user, null, 200));
        assertThat(history.get("items")).hasSize(1);
        long resultId = result.get("resultId").asLong();
        JsonNode reopened = json(perform(get("/results/" + resultId), user, null, 200));
        assertThat(reopened.get("saved").asBoolean()).isTrue();
        assertThat(json(perform(get("/sessions/" + sessionId), user, null, 200)).get("teamCode").isNull()).isFalse();

        // 고정 단어를 남기는 초기화: 남긴 단어는 하위 카테고리 바로 아래로, 나머지는 삭제, 뻗기 횟수 0
        JsonNode before = json(perform(get("/categories/" + catId + "/words").param("sessionId", String.valueOf(sessionId)), user, null, 200));
        String keepWord = before.get("groups").get(0).get("children").get(0).get("text").asText();
        JsonNode keepReset = json(perform(delete("/sessions/" + sessionId + "/categories/" + catId + "/words").param("keep", keepWord), user, null, 200));
        assertThat(keepReset.get("kept")).hasSize(1);
        JsonNode kept = json(perform(get("/categories/" + catId + "/words").param("sessionId", String.valueOf(sessionId)), user, null, 200));
        assertThat(kept.get("wordCount").asInt()).isEqualTo(1);
        assertThat(kept.get("groups").get(0).get("children").get(0).get("text").asText()).isEqualTo(keepWord);
        assertThat(kept.get("groups").get(0).get("expandCount").asInt()).isZero();
        // 남긴 단어에서 다시 뻗으면 1번째 뻗기로 센다
        long keptId = kept.get("groups").get(0).get("children").get(0).get("id").asLong();
        JsonNode reExpand = json(perform(post("/words/" + keptId + "/expand"), user, "{\"sessionId\":" + sessionId + "}", 200));
        assertThat(reExpand.get("expandCount").asInt()).isEqualTo(1);

        // 트리 초기화: 뻗은 단어가 모두 지워지고 뻗기 횟수도 0
        JsonNode reset = json(perform(delete("/sessions/" + sessionId + "/categories/" + catId + "/words"), user, null, 200));
        assertThat(reset.get("deleted").asInt()).isPositive();
        JsonNode cleared = json(perform(get("/categories/" + catId + "/words").param("sessionId", String.valueOf(sessionId)), user, null, 200));
        assertThat(cleared.get("wordCount").asInt()).isZero();
        assertThat(cleared.get("groups").get(0).get("expandCount").asInt()).isZero();
    }

    @Test
    void authRules() throws Exception {
        perform(get("/history"), null, null, 401);
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

    /** 뿌리들의 자식 단어를 깊이와 관계없이 모두 모은다 */
    private static void collect(JsonNode groups, Set<String> out) {
        groups.forEach(g -> collectWords(g.get("children"), out));
    }

    private static void collectWords(JsonNode children, Set<String> out) {
        children.forEach(c -> {
            out.add(c.get("text").asText());
            collectWords(c.get("children"), out);
        });
    }

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
