package com.applegend.seokkeobom.ai;

import com.applegend.seokkeobom.category.TemplateItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini generateContent REST 호출. API 키는 서버 환경변수에만 있고 브라우저로 나가지 않는다.
 * 아이디어·결과물은 품질 우선 모델(model), 단어 트리 가지 뻗기는 응답이 빠른 모델(wordModel)을 쓴다.
 * 503(사용량 많음)·500·응답 형식 오류는 잠시 뒤 같은 모델로 다시 시도한다.
 * 시간 초과·429(할당량)·404이거나 재시도도 실패하면 대체 모델들(fallbackModels)을 순서대로 시도한다. (위험 3)
 * 기본 모델 대기 시간을 짧게 두고(평소 응답 10~15초) 늦으면 빠른 대체 모델로 넘어가, 사용자가 1분 넘게 기다리지 않게 한다.
 */
public class GeminiAiClient implements AiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final int ATTEMPTS_PER_MODEL = 2;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestClient restClient;
    private final RestClient wordClient;
    private final RestClient fallbackClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String wordModel;
    /** 차례로 시도할 대체 모델들 (쉼표로 여러 개) */
    private final List<String> fallbackModels;

    public GeminiAiClient(String baseUrl, String apiKey, String model, String wordModel, String fallbackModel,
                          Duration timeout, Duration wordTimeout, Duration fallbackTimeout, ObjectMapper objectMapper) {
        this.restClient = client(baseUrl, apiKey, timeout);
        this.wordClient = client(baseUrl, apiKey, wordTimeout);
        this.fallbackClient = client(baseUrl, apiKey, fallbackTimeout);
        this.objectMapper = objectMapper;
        this.model = model;
        this.wordModel = (wordModel == null || wordModel.isBlank()) ? model : wordModel;
        this.fallbackModels = fallbackModel == null ? List.of()
                : java.util.Arrays.stream(fallbackModel.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static RestClient client(String baseUrl, String apiKey, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @Override
    public String mode() {
        return "gemini:" + model;
    }

    @Override
    public String wordMode() {
        return "gemini:" + wordModel;
    }

    @Override
    public IdeaDraft generateIdea(GenerationContext ctx) {
        JsonNode json = call(restClient, model, PromptBuilder.SYSTEM, PromptBuilder.ideaPrompt(ctx), 1.0);
        String title = text(json, "title");
        String summary = text(json, "summary");
        if (title.isBlank() || summary.isBlank()) {
            throw new AiException("AI가 아이디어를 완성하지 못했습니다. 다시 시도해 주세요.");
        }
        return new IdeaDraft(trim(title, 80), trim(summary, 800));
    }

    @Override
    public Map<String, String> generateResult(GenerationContext ctx) {
        JsonNode json = call(restClient, model, PromptBuilder.SYSTEM, PromptBuilder.resultPrompt(ctx), 0.8);
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

    @Override
    public List<String> generateWords(WordContext ctx) {
        JsonNode json = call(wordClient, wordModel, PromptBuilder.WORD_SYSTEM, PromptBuilder.wordsPrompt(ctx), 1.0);
        JsonNode arr = json.isArray() ? json : json.path("words");
        List<String> words = new ArrayList<>();
        arr.forEach(n -> {
            if (n.isTextual()) words.add(n.asText());
        });
        if (words.isEmpty()) {
            throw new AiException("AI가 새 단어를 만들지 못했어요. 다시 시도해 주세요.");
        }
        return words;
    }

    /** 같은 모델로 재시도한 뒤, 필요하면 대체 모델로 넘어간다. */
    private JsonNode call(RestClient client, String primary, String system, String prompt, double temperature) {
        List<String> models = new ArrayList<>(List.of(primary));
        for (String f : fallbackModels) {
            if (!models.contains(f)) models.add(f);
        }
        AiException last = null;
        for (int i = 0; i < models.size(); i++) {
            String m = models.get(i);
            RestClient c = i == 0 ? client : fallbackClient;
            for (int attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return callOnce(c, m, system, prompt, temperature);
                } catch (BusyException e) {
                    last = e;
                    if (!e.retrySameModel) break;
                    if (attempt < ATTEMPTS_PER_MODEL) sleep(RETRY_DELAY_MS * attempt);
                }
            }
            if (i < models.size() - 1) {
                log.warn("Gemini {} 실패, 대체 모델 {}로 다시 시도합니다.", m, models.get(i + 1));
            }
        }
        throw last;
    }

    private JsonNode callOnce(RestClient client, String m, String system, String prompt, double temperature) {
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "responseMimeType", "application/json"));
        String raw;
        try {
            raw = client.post()
                    .uri("/models/{model}:generateContent", m)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            // 시간 초과는 같은 모델로 다시 기다리면 대기 시간이 두 배가 되므로 바로 대체 모델로 넘어간다.
            log.warn("Gemini {} timeout/network error: {}", m, e.getMessage());
            throw new BusyException("AI 응답이 늦어지고 있습니다. 같은 조합으로 다시 시도해 주세요.", false, e);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Gemini {} HTTP {}: {}", m, status, trim(e.getResponseBodyAsString(), 300));
            if (status == 503 || status == 500) {
                throw new BusyException("지금 AI 사용량이 많아 응답하지 못했어요. 잠시 후 다시 시도해 주세요.", true, e);
            }
            if (status == 429) {
                throw new BusyException("AI 사용량이 많아 잠시 쉬어야 합니다. 1분쯤 뒤 다시 시도해 주세요.", false, e);
            }
            if (status == 404) {
                throw new BusyException("설정된 AI 모델을 쓸 수 없습니다. 관리자에게 알려 주세요.", false, e);
            }
            throw new AiException("AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (RestClientException e) {
            // 응답 본문을 읽는 도중 시간 초과(SocketTimeoutException) 등은 이쪽으로 온다.
            log.warn("Gemini {} response read failed: {}", m, e.getMostSpecificCause().toString());
            throw new BusyException("AI 응답이 늦어지고 있습니다. 같은 조합으로 다시 시도해 주세요.", false, e);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                String reason = root.path("candidates").path(0).path("finishReason").asText("");
                log.warn("Gemini empty response. finishReason={} promptFeedback={}", reason, root.path("promptFeedback"));
                throw new BusyException("AI가 결과를 돌려주지 않았습니다. 다른 조합으로 시도해 보세요.", false, null);
            }
            return objectMapper.readTree(stripFence(textNode.asText()));
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            // AI가 JSON 형식을 어긴 경우: 같은 모델로 한 번 더 시도할 만하다
            log.warn("Gemini {} response parse failed: {}", m, e.getMessage());
            throw new BusyException("AI 응답을 읽지 못했습니다. 다시 시도해 주세요.", true, e);
        }
    }

    /** 다시 시도하거나 대체 모델로 넘어갈 수 있는 실패 */
    private static class BusyException extends AiException {
        final boolean retrySameModel;

        BusyException(String message, boolean retrySameModel, Throwable cause) {
            super(message, cause);
            this.retrySameModel = retrySameModel;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
