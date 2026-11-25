package com.applegend.seokkeobom.category;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** /categories, /categories/{id}/words, /words/{id}/expand, 트리 초기화 */
class CategoryControllerTest extends ApiTestSupport {

    // ---------- GET /categories ----------

    @Test
    void listIsPublic() throws Exception {
        JsonNode cats = json(perform(get("/categories"), null, null, 200));
        assertThat(cats).hasSize(3);
        cats.forEach(c -> {
            assertThat(c.get("name").asText()).isNotBlank();
            assertThat(c.get("slug").asText()).isNotBlank();
        });
    }

    // ---------- GET /categories/{id}/words ----------

    @Test
    void wordsWithoutSessionShowsEmptyRoots() throws Exception {
        JsonNode tree = json(perform(get("/categories/" + categoryId(0) + "/words"), null, null, 200));
        assertThat(tree.get("groups")).hasSize(3);
        assertThat(tree.get("wordCount").asInt()).isZero();
        assertThat(tree.get("maxExpandsPerRoot").asInt()).isEqualTo(WordNode.MAX_EXPANDS_PER_ROOT);
    }

    @Test
    void wordsRejectsUnknownCategoryAndOutsiders() throws Exception {
        assertThat(code(perform(get("/categories/999999/words"), null, null, 404))).isEqualTo("CATEGORY_NOT_FOUND");
        long sessionId = createSession(signup()).get("sessionId").asLong();
        String url = "/categories/" + categoryId(0) + "/words";
        // 세션 트리는 로그인한 참여자만
        perform(get(url).param("sessionId", String.valueOf(sessionId)), null, null, 401);
        perform(get(url).param("sessionId", String.valueOf(sessionId)), signup(), null, 403);
    }

    // ---------- POST /words/{id}/expand ----------

    @Test
    void expandAddsDefaultThreeWords() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long cat = categoryId(0);
        JsonNode ex = expandFirstRoot(token, cat, sessionId);
        assertThat(ex.get("words")).hasSize(WordTreeService.DEFAULT_EXPAND);
        assertThat(ex.get("expandCount").asInt()).isEqualTo(1);
        assertThat(ex.get("remaining").asInt()).isEqualTo(WordNode.MAX_EXPANDS_PER_ROOT - 1);
        assertThat(tree(token, cat, sessionId).get("wordCount").asInt()).isEqualTo(WordTreeService.DEFAULT_EXPAND);
    }

    @Test
    void expandRejectsMissingSessionUnknownNodeAndOutsiders() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long rootId = tree(token, categoryId(0), sessionId).get("groups").get(0).get("id").asLong();
        String body = "{\"sessionId\":" + sessionId + "}";

        assertThat(code(perform(post("/words/" + rootId + "/expand"), token, "{}", 400))).isEqualTo("SESSION_REQUIRED");
        assertThat(code(perform(post("/words/999999/expand"), token, body, 404))).isEqualTo("WORD_NOT_FOUND");
        perform(post("/words/" + rootId + "/expand"), signup(), body, 403);
        perform(post("/words/" + rootId + "/expand"), null, body, 401);
    }

    @Test
    void expandRejectsWordFromAnotherSession() throws Exception {
        String token = signup();
        long cat = categoryId(0);
        long s1 = createSession(token).get("sessionId").asLong();
        long s2 = createSession(token).get("sessionId").asLong();
        long wordInS1 = expandFirstRoot(token, cat, s1).get("words").get(0).get("id").asLong();
        assertThat(code(perform(post("/words/" + wordInS1 + "/expand"), token, "{\"sessionId\":" + s2 + "}", 404)))
                .isEqualTo("WORD_NOT_FOUND");
    }

    // ---------- DELETE /sessions/{sessionId}/categories/{categoryId}/words ----------

    @Test
    void resetDeletesSessionWords() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long cat = categoryId(0);
        expandFirstRoot(token, cat, sessionId);

        JsonNode r = json(perform(delete("/sessions/" + sessionId + "/categories/" + cat + "/words"), token, null, 200));
        assertThat(r.get("deleted").asInt()).isEqualTo(WordTreeService.DEFAULT_EXPAND);
        assertThat(r.get("kept")).isEmpty();
        assertThat(tree(token, cat, sessionId).get("wordCount").asInt()).isZero();
    }

    @Test
    void resetKeepsGivenWords() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long cat = categoryId(0);
        String keep = expandFirstRoot(token, cat, sessionId).get("words").get(0).get("text").asText();

        JsonNode r = json(perform(delete("/sessions/" + sessionId + "/categories/" + cat + "/words").param("keep", keep),
                token, null, 200));
        assertThat(r.get("kept")).hasSize(1);
        assertThat(r.get("kept").get(0).asText()).isEqualTo(keep);
        assertThat(tree(token, cat, sessionId).get("wordCount").asInt()).isEqualTo(1);
    }

    @Test
    void resetRejectsOutsidersAndUnknownCategory() throws Exception {
        String token = signup();
        long sessionId = createSession(token).get("sessionId").asLong();
        long cat = categoryId(0);
        perform(delete("/sessions/" + sessionId + "/categories/" + cat + "/words"), signup(), null, 403);
        assertThat(code(perform(delete("/sessions/" + sessionId + "/categories/999999/words"), token, null, 404)))
                .isEqualTo("CATEGORY_NOT_FOUND");
    }
}
