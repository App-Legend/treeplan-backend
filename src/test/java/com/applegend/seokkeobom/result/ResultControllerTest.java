package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** /ideas/{ideaId}/result, /results/** */
class ResultControllerTest extends ApiTestSupport {

    /** 방장이 결과물까지 만든 상태 */
    private record Made(Drawn drawn, long ideaId, JsonNode result) {
        String token() { return drawn.token(); }
        long id() { return result.get("resultId").asLong(); }
    }

    private Made made() throws Exception {
        Drawn d = drawn();
        long ideaId = idea(d.token(), d.drawId()).get("ideaId").asLong();
        return new Made(d, ideaId, result(d.token(), ideaId));
    }

    private String member(Made m) throws Exception {
        String token = signup();
        join(token, m.drawn().teamCode());
        return token;
    }

    // ---------- POST /ideas/{ideaId}/result ----------

    @Test
    void createsResultWithTemplateItems() throws Exception {
        JsonNode r = made().result();
        assertThat(r.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(r.get("saved").asBoolean()).isTrue();
        assertThat(r.get("canDelete").asBoolean()).isTrue();
        assertThat(r.get("words")).hasSize(3);
        assertThat(r.get("items")).isNotEmpty();
        r.get("items").forEach(i -> assertThat(i.get("key").asText()).isNotBlank());
    }

    @Test
    void createReturnsExistingResultForSameIdea() throws Exception {
        Made m = made();
        assertThat(result(m.token(), m.ideaId()).get("resultId").asLong()).isEqualTo(m.id());
    }

    @Test
    void createRejectsUnknownIdeaAndOutsiders() throws Exception {
        Made m = made();
        assertThat(code(perform(post("/ideas/999999/result"), m.token(), null, 404))).isEqualTo("IDEA_NOT_FOUND");
        perform(post("/ideas/" + m.ideaId() + "/result"), signup(), null, 403);
    }

    // ---------- GET /results/{id} ----------

    @Test
    void getIsVisibleToMembersOnly() throws Exception {
        Made m = made();
        JsonNode asMember = json(perform(get("/results/" + m.id()), member(m), null, 200));
        assertThat(asMember.get("canDelete").asBoolean()).isFalse();
        perform(get("/results/" + m.id()), signup(), null, 403);
        assertThat(code(perform(get("/results/999999"), m.token(), null, 404))).isEqualTo("RESULT_NOT_FOUND");
    }

    // ---------- PATCH /results/{id} ----------

    @Test
    void updateEditsTemplateItem() throws Exception {
        Made m = made();
        String key = m.result().get("items").get(0).get("key").asText();
        JsonNode r = json(perform(patch("/results/" + m.id()), m.token(), "{\"items\":{\"" + key + "\":\"직접 고침\"}}", 200));
        assertThat(r.get("edited").asBoolean()).isTrue();
        assertThat(r.get("items").get(0).get("content").asText()).isEqualTo("직접 고침");
    }

    @Test
    void updateRejectsBadChanges() throws Exception {
        Made m = made();
        String key = m.result().get("items").get(0).get("key").asText();
        String url = "/results/" + m.id();
        assertThat(code(perform(patch(url), m.token(), "{\"items\":{}}", 400))).isEqualTo("NO_CHANGES");
        assertThat(code(perform(patch(url), m.token(), "{\"items\":{\"unknown\":\"x\"}}", 400))).isEqualTo("TEMPLATE_MISMATCH");
        assertThat(code(perform(patch(url), m.token(), "{\"items\":{\"" + key + "\":\"" + "가".repeat(5001) + "\"}}", 400)))
                .isEqualTo("TOO_LONG");
        perform(patch(url), signup(), "{\"items\":{\"" + key + "\":\"x\"}}", 403);
    }

    // ---------- POST /results/{id}/regenerate ----------

    @Test
    void regenerateWithAndWithoutFeedback() throws Exception {
        Made m = made();
        String url = "/results/" + m.id() + "/regenerate";
        assertThat(json(perform(post(url), m.token(), "{\"feedback\":\"더 자세히\"}", 200)).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json(perform(post(url), m.token(), null, 200)).get("resultId").asLong()).isEqualTo(m.id());
    }

    @Test
    void regenerateRejectsLongFeedbackAndOutsiders() throws Exception {
        Made m = made();
        String url = "/results/" + m.id() + "/regenerate";
        assertThat(code(perform(post(url), m.token(), "{\"feedback\":\"" + "가".repeat(301) + "\"}", 400))).isEqualTo("TOO_LONG");
        perform(post(url), signup(), "{}", 403);
    }

    // ---------- GET /results/{id}/export ----------

    @Test
    void exportsMarkdownAndText() throws Exception {
        Made m = made();
        String title = m.result().get("title").asText();

        MvcResult md = perform(get("/results/" + m.id() + "/export").param("format", "md"), m.token(), null, 200);
        assertThat(md.getResponse().getContentType()).startsWith("text/markdown");
        assertThat(md.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains(".md");
        assertThat(md.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(title);

        MvcResult txt = perform(get("/results/" + m.id() + "/export").param("format", "txt"), m.token(), null, 200);
        assertThat(txt.getResponse().getContentType()).startsWith("text/plain");
        assertThat(txt.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(title).contains("사용한 단어");
    }

    @Test
    void exportRejectsBadFormatAndOutsiders() throws Exception {
        Made m = made();
        String url = "/results/" + m.id() + "/export";
        assertThat(code(perform(get(url).param("format", "docx"), m.token(), null, 400))).isEqualTo("UNSUPPORTED_FORMAT");
        assertThat(code(perform(get(url), m.token(), null, 400))).isEqualTo("BAD_REQUEST");
        perform(get(url).param("format", "md"), signup(), null, 403);
    }

    // ---------- DELETE /results/{id} ----------

    @Test
    void onlyOwnerDeletes() throws Exception {
        Made m = made();
        assertThat(code(perform(delete("/results/" + m.id()), member(m), null, 403))).isEqualTo("NOT_OWNER");
        assertThat(json(perform(delete("/results/" + m.id()), m.token(), null, 200)).get("success").asBoolean()).isTrue();
        perform(get("/results/" + m.id()), m.token(), null, 404);
        perform(delete("/results/" + m.id()), m.token(), null, 404);
    }
}
