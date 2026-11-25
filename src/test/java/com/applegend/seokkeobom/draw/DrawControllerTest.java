package com.applegend.seokkeobom.draw;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** POST /sessions/{sessionId}/draws */
class DrawControllerTest extends ApiTestSupport {

    /** 세션 하나에 단어 3개를 뻗어 두고, 그 단어들을 함께 돌려준다 */
    private record Ready(String token, long sessionId, long categoryId, List<String> words) {}

    private Ready ready() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long cat = categoryId(0);
        List<String> words = new ArrayList<>();
        expandFirstRoot(token, cat, sessionId).get("words").forEach(w -> words.add(w.get("text").asText()));
        return new Ready(token, sessionId, cat, words);
    }

    private String url(Ready r) {
        return "/sessions/" + r.sessionId() + "/draws";
    }

    @Test
    void drawsThreeWordsFromTree() throws Exception {
        Ready r = ready();
        JsonNode d = draw(r.token(), r.sessionId(), r.categoryId());
        List<String> words = om.convertValue(d.get("words"), List.class);
        assertThat(words).hasSize(3).doesNotHaveDuplicates();
        assertThat(r.words()).containsAll(words);
        assertThat(d.get("categoryId").asLong()).isEqualTo(r.categoryId());
        assertThat(d.get("drawId").asLong()).isPositive();
    }

    @Test
    void keepsFixedWords() throws Exception {
        Ready r = ready();
        String body = "{\"categoryIds\":[" + r.categoryId() + "],\"fixedWords\":" + om.writeValueAsString(r.words()) + "}";
        JsonNode d = json(perform(post(url(r)), r.token(), body, 201));
        assertThat(om.convertValue(d.get("words"), List.class)).containsExactlyElementsOf(r.words());
    }

    @Test
    void rejectsMissingOrMixedCategories() throws Exception {
        Ready r = ready();
        assertThat(code(perform(post(url(r)), r.token(), "{\"categoryIds\":[]}", 400))).isEqualTo("CATEGORY_REQUIRED");
        assertThat(code(perform(post(url(r)), r.token(), "{\"categoryIds\":[" + r.categoryId() + "," + categoryId(1) + "]}", 400)))
                .isEqualTo("SINGLE_CATEGORY_ONLY");
    }

    @Test
    void rejectsEmptyTree() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        assertThat(code(perform(post("/sessions/" + sessionId + "/draws"), token, "{\"categoryIds\":[" + categoryId(0) + "]}", 422)))
                .isEqualTo("TREE_TOO_SMALL");
    }

    @Test
    void rejectsBadFixedWords() throws Exception {
        Ready r = ready();
        String tooMany = "{\"categoryIds\":[" + r.categoryId() + "],\"fixedWords\":[\"a\",\"b\",\"c\",\"d\"]}";
        assertThat(code(perform(post(url(r)), r.token(), tooMany, 400))).isEqualTo("TOO_MANY_FIXED");
        String foreign = "{\"categoryIds\":[" + r.categoryId() + "],\"fixedWords\":[\"트리에없는단어\"]}";
        assertThat(code(perform(post(url(r)), r.token(), foreign, 400))).isEqualTo("FIXED_NOT_IN_CATEGORY");
    }

    @Test
    void rejectsWhenExclusionsLeaveTooFewWords() throws Exception {
        Ready r = ready();
        String body = "{\"categoryIds\":[" + r.categoryId() + "],\"excludedWords\":[\"" + r.words().get(0) + "\"]}";
        assertThat(code(perform(post(url(r)), r.token(), body, 422))).isEqualTo("POOL_TOO_SMALL");
    }

    @Test
    void rejectsOutsiders() throws Exception {
        Ready r = ready();
        String body = "{\"categoryIds\":[" + r.categoryId() + "]}";
        perform(post(url(r)), signup(), body, 403);
        perform(post(url(r)), null, body, 401);
    }

    @Test
    void rejectsClosedSession() throws Exception {
        Ready r = ready();
        perform(post("/sessions/" + r.sessionId() + "/close"), r.token(), null, 200);
        assertThat(code(perform(post(url(r)), r.token(), "{\"categoryIds\":[" + r.categoryId() + "]}", 409)))
                .isEqualTo("SESSION_CLOSED");
    }
}
