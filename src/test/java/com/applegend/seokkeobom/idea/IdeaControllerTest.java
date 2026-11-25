package com.applegend.seokkeobom.idea;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** POST /draws/{drawId}/idea, PUT /ideas/{ideaId}/bookmark */
class IdeaControllerTest extends ApiTestSupport {

    // ---------- POST /draws/{drawId}/idea ----------

    @Test
    void generatesIdeaFromDraw() throws Exception {
        Drawn d = drawn();
        JsonNode idea = idea(d.token(), d.drawId());
        assertThat(idea.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(idea.get("title").asText()).isNotBlank();
        assertThat(idea.get("summary").asText()).isNotBlank();
        assertThat(idea.get("drawId").asLong()).isEqualTo(d.drawId());
        assertThat(idea.get("words")).hasSize(3);
        assertThat(idea.get("bookmarked").asBoolean()).isFalse();
    }

    @Test
    void sameDrawCanBeRegeneratedUntilAdopted() throws Exception {
        Drawn d = drawn();
        long first = idea(d.token(), d.drawId()).get("ideaId").asLong();
        // 결과물로 만들기 전에는 같은 조합으로 다시 만들 수 있고, 같은 아이디어를 갱신한다
        assertThat(idea(d.token(), d.drawId()).get("ideaId").asLong()).isEqualTo(first);
        result(d.token(), first);
        assertThat(code(perform(post("/draws/" + d.drawId() + "/idea"), d.token(), null, 409))).isEqualTo("ALREADY_ADOPTED");
    }

    @Test
    void generateRejectsUnknownDrawAndOutsiders() throws Exception {
        Drawn d = drawn();
        assertThat(code(perform(post("/draws/999999/idea"), d.token(), null, 404))).isEqualTo("DRAW_NOT_FOUND");
        perform(post("/draws/" + d.drawId() + "/idea"), signup(), null, 403);
        perform(post("/draws/" + d.drawId() + "/idea"), null, null, 401);
    }

    // ---------- PUT /ideas/{ideaId}/bookmark ----------

    @Test
    void bookmarkToggles() throws Exception {
        Drawn d = drawn();
        long ideaId = idea(d.token(), d.drawId()).get("ideaId").asLong();

        JsonNode on = json(perform(put("/ideas/" + ideaId + "/bookmark"), d.token(), "{\"bookmarked\":true}", 200));
        assertThat(on.get("bookmarked").asBoolean()).isTrue();
        assertThat(on.get("bookmarkedAt").isNull()).isFalse();
        JsonNode off = json(perform(put("/ideas/" + ideaId + "/bookmark"), d.token(), "{\"bookmarked\":false}", 200));
        assertThat(off.get("bookmarked").asBoolean()).isFalse();
    }

    @Test
    void bookmarkRejectsMissingFlagUnknownIdeaAndOutsiders() throws Exception {
        Drawn d = drawn();
        long ideaId = idea(d.token(), d.drawId()).get("ideaId").asLong();
        assertThat(code(perform(put("/ideas/" + ideaId + "/bookmark"), d.token(), "{}", 400))).isEqualTo("BAD_REQUEST");
        assertThat(code(perform(put("/ideas/999999/bookmark"), d.token(), "{\"bookmarked\":true}", 404))).isEqualTo("IDEA_NOT_FOUND");
        perform(put("/ideas/" + ideaId + "/bookmark"), signup(), "{\"bookmarked\":true}", 403);
    }
}
