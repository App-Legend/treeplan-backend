package com.applegend.seokkeobom.common;

import com.applegend.seokkeobom.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** /common/health, /common/deploy-demo (로그인 없이 호출) */
class HealthControllerTest extends ApiTestSupport {

    @Test
    void healthIsPublicAndReportsDatabaseUp() throws Exception {
        JsonNode body = json(perform(get("/common/health"), null, null, 200));
        assertThat(body.get("status").asText()).isEqualTo("ok");
        assertThat(body.get("database").asText()).isEqualTo("up");
    }

    @Test
    void deployDemoIsPublic() throws Exception {
        JsonNode body = json(perform(get("/common/deploy-demo"), null, null, 200));
        assertThat(body.get("version").asText()).isEqualTo("team-v1");
        assertThat(body.get("pod").asText()).isNotBlank();
    }
}
