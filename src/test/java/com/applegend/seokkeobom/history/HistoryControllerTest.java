package com.applegend.seokkeobom.history;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** GET /history */
class HistoryControllerTest extends ApiTestSupport {

    @Test
    void requiresRegisteredUser() throws Exception {
        perform(get("/history"), null, null, 401);
        assertThat(code(perform(get("/history"), guest(), null, 403))).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void emptyForNewUser() throws Exception {
        JsonNode h = json(perform(get("/history"), signup(), null, 200));
        assertThat(h.get("items")).isEmpty();
        assertThat(h.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void listsResultsAndBookmarkedIdeas() throws Exception {
        Drawn d = drawn();
        // 결과물 하나
        long adopted = idea(d.token(), d.drawId()).get("ideaId").asLong();
        result(d.token(), adopted);
        // 보관한 아이디어 하나 (다른 조합)
        long otherDraw = draw(d.token(), d.sessionId(), d.categoryId()).get("drawId").asLong();
        long kept = idea(d.token(), otherDraw).get("ideaId").asLong();
        perform(put("/ideas/" + kept + "/bookmark"), d.token(), "{\"bookmarked\":true}", 200);

        JsonNode items = json(perform(get("/history"), d.token(), null, 200)).get("items");
        Set<String> types = new HashSet<>();
        items.forEach(i -> types.add(i.get("type").asText()));
        assertThat(types).containsExactlyInAnyOrder("RESULT", "IDEA");
    }

    @Test
    void filtersByQueryAndCategory() throws Exception {
        Drawn d = drawn();
        result(d.token(), idea(d.token(), d.drawId()).get("ideaId").asLong());

        assertThat(json(perform(get("/history").param("categoryId", String.valueOf(d.categoryId())), d.token(), null, 200))
                .get("items")).hasSize(1);
        assertThat(json(perform(get("/history").param("categoryId", String.valueOf(categoryId(1))), d.token(), null, 200))
                .get("items")).isEmpty();
        assertThat(json(perform(get("/history").param("q", "절대없을검색어"), d.token(), null, 200)).get("items")).isEmpty();
    }

    @Test
    void doesNotShowOtherUsersItems() throws Exception {
        Drawn d = drawn();
        result(d.token(), idea(d.token(), d.drawId()).get("ideaId").asLong());
        assertThat(json(perform(get("/history"), signup(), null, 200)).get("items")).isEmpty();
    }

    @Test
    void rejectsBadCursor() throws Exception {
        String token = signup();
        createSession(token);
        assertThat(code(perform(get("/history").param("cursor", "!!!"), token, null, 400))).isEqualTo("BAD_CURSOR");
    }
}
