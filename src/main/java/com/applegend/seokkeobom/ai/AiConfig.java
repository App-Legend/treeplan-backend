package com.applegend.seokkeobom.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiClient aiClient(@Value("${app.ai.gemini-api-key}") String apiKey,
                             @Value("${app.ai.gemini-model}") String model,
                             @Value("${app.ai.gemini-base-url}") String baseUrl,
                             @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
                             ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY가 없어 예시(mock) 모드로 실행합니다. 실제 AI 결과가 아닙니다.");
            return new MockAiClient();
        }
        log.info("Gemini 모델 {} 사용", model);
        return new GeminiAiClient(baseUrl, apiKey, model, Duration.ofSeconds(timeoutSeconds), objectMapper);
    }
}
