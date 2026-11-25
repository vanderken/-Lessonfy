package com.csit321g1.bairus.lessonfy.conversion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fast backup provider using Groq's OpenAI-compatible chat completions API. */
@Component
public class GroqAiLessonProvider implements AiLessonProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqAiLessonProvider.class);
    private static final String PROVIDER = "groq";
    private static final int MAX_OUTPUT_TOKENS = 8192;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${lessonfy.ai.groq.api-key:}")
    private String apiKey;

    @Value("${lessonfy.ai.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${lessonfy.ai.groq.model:openai/gpt-oss-20b}")
    private String model;

    @Value("${lessonfy.ai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${lessonfy.ai.max-input-chars:20000}")
    private int maxInputChars;

    public GroqAiLessonProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint) {
        return generate(documentText, titleHint, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint, Duration timeout) {
        String responseBody = send(buildRequestBody(documentText, titleHint), timeout);
        return parseResponse(responseBody);
    }

    /** Package-private so the request shape can be tested directly. */
    Map<String, Object> buildRequestBody(String documentText, String titleHint) {
        String source = AiLessonSupport.prepareSource(documentText, maxInputChars, PROVIDER);

        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "lesson_draft",
                        "strict", true,
                        "schema", AiLessonSupport.standardLessonSchema()));

        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", AiLessonSupport.SYSTEM_PROMPT),
                        Map.of("role", "user", "content", AiLessonSupport.formatUserPrompt(titleHint, source))),
                "response_format", responseFormat,
                "max_tokens", MAX_OUTPUT_TOKENS);
    }

    /** Package-private so output validation can be tested directly. */
    GeneratedLesson parseResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root != null
                    ? root.path("choices").path(0).path("message").path("content").asText("")
                    : "";
            return AiLessonSupport.parseLessonJson(objectMapper, content, PROVIDER);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiLessonSupport.badOutput(PROVIDER);
        }
    }

    private String send(Map<String, Object> body, Duration timeout) {
        if (!isConfigured()) {
            throw new AiProviderException(AiProviderException.Kind.NOT_CONFIGURED, PROVIDER,
                    "Groq is not configured. Add GROQ_API_KEY to the backend environment and restart Spring Boot.");
        }

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .timeout(effectiveTimeout(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 400) {
                log.warn("Groq rejected the request (HTTP 400).");
                throw new AiProviderException(AiProviderException.Kind.INVALID_REQUEST, PROVIDER,
                        "Groq rejected the request. Check GROQ_MODEL, then try again or use Semantic-Based Conversion.");
            }
            if (status == 401 || status == 403) {
                log.warn("Groq authentication failed (HTTP {}).", status);
                throw new AiProviderException(AiProviderException.Kind.AUTH, PROVIDER,
                        "The Groq API key is missing or invalid. Check GROQ_API_KEY and restart the backend.");
            }
            if (status == 429) {
                log.warn("Groq rate limit reached (HTTP 429).");
                throw new AiProviderException(AiProviderException.Kind.QUOTA, PROVIDER,
                        "Groq's free rate limit is full right now. Trying another AI, or use Semantic-Based Conversion.");
            }
            if (status < 200 || status >= 300) {
                log.warn("Groq returned HTTP {}.", status);
                throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                        "Groq is temporarily unavailable. Trying another AI, or use Semantic-Based Conversion.");
            }
            return response.body();
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException(AiProviderException.Kind.TIMEOUT, PROVIDER,
                    "Groq took too long to respond. Trying another AI, or use Semantic-Based Conversion.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                    "AI generation was interrupted. Please try again.");
        } catch (IOException ex) {
            log.warn("Could not contact Groq: {}", ex.getMessage());
            throw new AiProviderException(AiProviderException.Kind.NETWORK, PROVIDER,
                    "Cannot reach Groq. Trying another AI, or use Semantic-Based Conversion.");
        }
    }

    private Duration effectiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofSeconds(Math.max(1, timeoutSeconds));
        }
        return timeout;
    }
}
