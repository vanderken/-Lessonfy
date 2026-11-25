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

/** Google Gemini provider. The key stays in Spring Boot and is never sent to React. */
@Component
public class GeminiAiLessonProvider implements AiLessonProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiLessonProvider.class);
    private static final String PROVIDER = "gemini";
    private static final int MAX_OUTPUT_TOKENS = 8192;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${lessonfy.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${lessonfy.ai.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${lessonfy.ai.gemini.model:gemini-3.5-flash-lite}")
    private String model;

    @Value("${lessonfy.ai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${lessonfy.ai.max-input-chars:20000}")
    private int maxInputChars;

    public GeminiAiLessonProvider(ObjectMapper objectMapper) {
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

    /** Package-private so the prompt and truncation can be tested directly. */
    Map<String, Object> buildRequestBody(String documentText, String titleHint) {
        String source = AiLessonSupport.prepareSource(documentText, maxInputChars, PROVIDER);

        return Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", AiLessonSupport.SYSTEM_PROMPT))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", AiLessonSupport.formatUserPrompt(titleHint, source))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", AiLessonSupport.geminiLessonSchema(),
                        "maxOutputTokens", MAX_OUTPUT_TOKENS));
    }

    /** Package-private so AI output validation can be tested directly. */
    GeneratedLesson parseResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root != null
                    ? root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("")
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
                    "Gemini is not configured. Add GEMINI_API_KEY to the backend environment and restart Spring Boot.");
        }

        Duration requestTimeout = effectiveTimeout(timeout);
        String url = baseUrl.replaceAll("/+$", "")
                + "/v1beta/models/" + model + ":generateContent";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey.trim())
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 400) {
                log.warn("Gemini rejected the request (HTTP 400).");
                throw new AiProviderException(AiProviderException.Kind.INVALID_REQUEST, PROVIDER,
                        "Gemini rejected the request. Check GEMINI_MODEL, then try again or use Semantic-Based Conversion.");
            }
            if (status == 401 || status == 403) {
                log.warn("Gemini authentication failed (HTTP {}).", status);
                throw new AiProviderException(AiProviderException.Kind.AUTH, PROVIDER,
                        "The Gemini API key is missing or invalid. Check GEMINI_API_KEY and restart the backend.");
            }
            if (status == 404) {
                log.warn("Gemini model not found: {}.", model);
                throw new AiProviderException(AiProviderException.Kind.INVALID_REQUEST, PROVIDER,
                        "The Gemini model '" + model + "' was not found. Check GEMINI_MODEL.");
            }
            if (status == 429) {
                log.warn("Gemini quota reached (HTTP 429).");
                throw new AiProviderException(AiProviderException.Kind.QUOTA, PROVIDER,
                        "Gemini's free quota is full right now. Trying a backup AI, or use Semantic-Based Conversion.");
            }
            if (status < 200 || status >= 300) {
                log.warn("Gemini returned HTTP {}.", status);
                throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                        "Gemini is temporarily unavailable. Trying a backup AI, or use Semantic-Based Conversion.");
            }
            return response.body();
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException(AiProviderException.Kind.TIMEOUT, PROVIDER,
                    "Gemini took too long to respond. Trying a backup AI, or use Semantic-Based Conversion.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                    "AI generation was interrupted. Please try again.");
        } catch (IOException ex) {
            log.warn("Could not contact Gemini: {}", ex.getMessage());
            throw new AiProviderException(AiProviderException.Kind.NETWORK, PROVIDER,
                    "Cannot reach Gemini. Trying a backup AI, or use Semantic-Based Conversion.");
        }
    }

    private Duration effectiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofSeconds(Math.max(1, timeoutSeconds));
        }
        return timeout;
    }
}
