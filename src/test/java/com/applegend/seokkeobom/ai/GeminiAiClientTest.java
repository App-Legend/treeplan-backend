package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.TemplateItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 Gemini 대신 로컬 HTTP 서버를 띄워 재시도·대체 모델·응답 파싱을 검증한다.
 * 모델마다 돌려줄 응답(상태 코드, 본문)을 차례로 쌓아 두고, 호출된 모델 순서를 기록한다.
 */
class GeminiAiClientTest {
    private static final GenerationContext IDEA = new GenerationContext("food", "음식", List.of("김치", "우주", "택배"),
            "제목", "요약", List.of(new TemplateItem("concept", "콘셉트", "설명"),
                    new TemplateItem("steps", "단계", "설명"), new TemplateItem("reason", "조합 이유", "설명")),
            "지침", null);
    private static final WordContext WORDS = new WordContext("food", "음식", "재료", List.of(), List.of(), 3, List.of("색"));

    private HttpServer server;
    private final Map<String, Deque<Object[]>> responses = new ConcurrentHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile long delayMs;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", ex -> {
            // 경로: /models/{model}:generateContent
            String model = ex.getRequestURI().getPath().substring("/models/".length()).split(":")[0];
            calls.add(model);
            Object[] r = responses.getOrDefault(model, new ArrayDeque<>()).poll();
            if (r == null) r = new Object[] {500, "{}"};
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders((int) r[0], body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        // 시간 초과 테스트에서 앞 요청이 잠든 동안에도 다음 요청을 바로 받도록 요청마다 스레드를 쓴다
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiAiClient client(Duration timeout) {
        return new GeminiAiClient("http://127.0.0.1:" + server.getAddress().getPort(), "key",
                "main", "word", "fb1, fb2", timeout, timeout, timeout, new ObjectMapper());
    }

    private GeminiAiClient client() {
        return client(Duration.ofSeconds(5));
    }

    private void respond(String model, int status, String body) {
        responses.computeIfAbsent(model, k -> new ArrayDeque<>()).add(new Object[] {status, body});
    }

    /** Gemini 응답 봉투에 모델이 만든 텍스트를 담는다 */
    private static String gemini(String text) throws Exception {
        return new ObjectMapper().writeValueAsString(
                Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @Test
    void parsesIdeaInsideCodeFence() throws Exception {
        respond("main", 200, gemini("```json\n{\"title\":\"김치 우주 택배\",\"summary\":\"요약입니다\"}\n```"));
        IdeaDraft idea = client().generateIdea(IDEA);
        assertThat(idea.title()).isEqualTo("김치 우주 택배");
        assertThat(idea.summary()).isEqualTo("요약입니다");
        assertThat(calls).containsExactly("main");
    }

    @Test
    void retriesSameModelOn503() throws Exception {
        respond("main", 503, "{}");
        respond("main", 200, gemini("{\"title\":\"t\",\"summary\":\"s\"}"));
        assertThat(client().generateIdea(IDEA).title()).isEqualTo("t");
        assertThat(calls).containsExactly("main", "main");
    }

    @Test
    void fallsBackInOrderOn429WithoutRetryingSameModel() throws Exception {
        respond("main", 429, "{}");
        respond("fb1", 404, "{}");
        respond("fb2", 200, gemini("{\"title\":\"t\",\"summary\":\"s\"}"));
        assertThat(client().generateIdea(IDEA).title()).isEqualTo("t");
        assertThat(calls).containsExactly("main", "fb1", "fb2");
    }

    @Test
    void fallsBackOnTimeout() throws Exception {
        delayMs = 1500;
        respond("main", 200, gemini("{\"title\":\"늦음\",\"summary\":\"s\"}"));
        respond("fb1", 200, gemini("{\"title\":\"늦음\",\"summary\":\"s\"}"));
        respond("fb2", 200, gemini("{\"title\":\"늦음\",\"summary\":\"s\"}"));
        assertThatThrownBy(() -> client(Duration.ofMillis(300)).generateIdea(IDEA))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("늦어지고");
        // 시간 초과는 같은 모델로 다시 기다리지 않는다
        assertThat(calls).containsExactly("main", "fb1", "fb2");
    }

    @Test
    void clientErrorStopsWithoutFallback() {
        respond("main", 400, "{\"error\":\"bad\"}");
        assertThatThrownBy(() -> client().generateIdea(IDEA))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("연결하지 못했습니다");
        assertThat(calls).containsExactly("main");
    }

    @Test
    void blankIdeaIsRejected() throws Exception {
        respond("main", 200, gemini("{\"title\":\"\",\"summary\":\"s\"}"));
        assertThatThrownBy(() -> client().generateIdea(IDEA)).isInstanceOf(AiException.class);
    }

    @Test
    void resultJoinsArraysAndAllowsOneMissingItem() throws Exception {
        respond("main", 200, gemini("{\"concept\":\"콘셉트\",\"steps\":[\"하나\",\"둘\"]}"));
        Map<String, String> body = client().generateResult(IDEA);
        assertThat(body).containsOnlyKeys("concept", "steps", "reason");
        assertThat(body.get("steps")).isEqualTo("- 하나\n- 둘");
        assertThat(body.get("reason")).isEmpty();
    }

    @Test
    void resultMostlyEmptyIsRejected() throws Exception {
        respond("main", 200, gemini("{\"concept\":\"콘셉트\"}"));
        assertThatThrownBy(() -> client().generateResult(IDEA))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("양식");
    }

    @Test
    void wordsUseWordModelAndAcceptBareArray() throws Exception {
        respond("word", 200, gemini("[\"배추\", 3, \"고춧가루\"]"));
        assertThat(client().generateWords(WORDS)).containsExactly("배추", "고춧가루");
        assertThat(calls).containsExactly("word");
    }

    @Test
    void emptyWordsAreRejected() throws Exception {
        respond("word", 200, gemini("{\"words\":[]}"));
        assertThatThrownBy(() -> client().generateWords(WORDS)).isInstanceOf(AiException.class);
    }
}
