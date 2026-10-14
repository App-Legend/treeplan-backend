package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.TemplateItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gemini generateContent REST 호출. API 키는 서버 환경변수에만 있고 브라우저로 나가지 않는다. */
public class GeminiAiClient implements AiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiAiClient(String baseUrl, String apiKey, String model, Duration timeout, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public String mode() {
        return "gemini:" + model;
    }

    @Override
    public IdeaDraft generateIdea(GenerationContext ctx) {
        JsonNode json = call(PromptBuilder.ideaPrompt(ctx), 1.0);
        String title = text(json, "title");
        String summary = text(json, "summary");
        if (title.isBlank() || summary.isBlank()) {
            throw new AiException("AI가 아이디어를 완성하지 못했습니다. 다시 시도해 주세요.");
        }
        return new IdeaDraft(trim(title, 80), trim(summary, 800));
    }

    @Override
    public Map<String, String> generateResult(GenerationContext ctx) {
        JsonNode json = call(PromptBuilder.resultPrompt(ctx), 0.8);
        Map<String, String> body = new LinkedHashMap<>();
        int filled = 0;
        for (TemplateItem item : ctx.items()) {
            String value = text(json, item.key());
            if (!value.isBlank()) filled++;
            body.put(item.key(), value);
        }
        if (filled < Math.max(1, ctx.items().size() - 1)) {
            // 대부분 비어 있으면 실패로 본다. 한 항목 정도 빠진 경우는 '보완 요청'으로 채우게 둔다.
            throw new AiException("AI 응답이 양식을 충분히 채우지 못했습니다. 다시 시도해 주세요.");
        }
        return body;
    }

    private JsonNode call(String prompt, double temperature) {
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", PromptBuilder.SYSTEM))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "responseMimeType", "application/json"));
        String raw;
        try {
            raw = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            log.warn("Gemini timeout/network error: {}", e.getMessage());
            throw new AiException("AI 응답이 늦어지고 있습니다. 같은 조합으로 다시 시도해 주세요.", e);
        } catch (RestClientResponseException e) {
            log.warn("Gemini HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new AiException("AI 사용량이 많아 잠시 쉬어야 합니다. 1분쯤 뒤 다시 시도해 주세요.", e);
            }
            throw new AiException("AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                String reason = root.path("candidates").path(0).path("finishReason").asText("");
                log.warn("Gemini empty response. finishReason={} promptFeedback={}", reason, root.path("promptFeedback"));
                throw new AiException("AI가 결과를 돌려주지 않았습니다. 다른 조합으로 시도해 보세요.");
            }
            return objectMapper.readTree(stripFence(textNode.asText()));
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini response parse failed: {}", e.getMessage());
            throw new AiException("AI 응답을 읽지 못했습니다. 다시 시도해 주세요.", e);
        }
    }

    private static String stripFence(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) t = t.substring(first + 1, last);
        }
        return t.strip();
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.path(field);
        if (n.isMissingNode() || n.isNull()) return "";
        if (n.isArray()) {
            StringBuilder sb = new StringBuilder();
            n.forEach(el -> sb.append("- ").append(el.asText()).append('\n'));
            return sb.toString().strip();
        }
        return n.asText("").strip();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
